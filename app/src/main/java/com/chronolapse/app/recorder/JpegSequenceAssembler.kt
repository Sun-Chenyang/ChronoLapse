package com.chronolapse.app.recorder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.Image
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Encodes a sequence of JPEG files into an H.264 MP4 using the hardware
 * video encoder in buffer-to-buffer mode. Runs on a background thread.
 */
class JpegSequenceAssembler {

    fun assemble(jpegs: List<File>, output: File, fps: Int, width: Int, height: Int) {
        val w = width and 1.inv()
        val h = height and 1.inv()
        require(jpegs.isNotEmpty()) { "No frames to assemble" }
        require(w >= 32 && h >= 32) { "Invalid frame size" }

        val frameDurationUs = 1_000_000L / fps
        val bitRate = maxOf(1_000_000, w * h * fps / 6)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            var muxerStarted = false
            var trackIndex = -1
            var ptsUs = 0L
            val info = MediaCodec.BufferInfo()

            for (jpeg in jpegs) {
                val frame = decodeUpright(jpeg, w, h) ?: continue

                var inIndex = codec.dequeueInputBuffer(50_000L)
                while (inIndex < 0) {
                    trackIndex = drain(codec, muxer, info, trackIndex)
                    inIndex = codec.dequeueInputBuffer(50_000L)
                }

                fillYuvFromBitmap(codec.getInputImage(inIndex)!!, frame, w, h)
                frame.recycle()

                codec.queueInputBuffer(inIndex, 0, w * h * 3 / 2, ptsUs, 0)
                ptsUs += frameDurationUs

                trackIndex = drain(codec, muxer, info, trackIndex)
            }

            var inIndex = codec.dequeueInputBuffer(50_000L)
            while (inIndex < 0) {
                trackIndex = drain(codec, muxer, info, trackIndex)
                inIndex = codec.dequeueInputBuffer(50_000L)
            }
            codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)

            while (true) {
                val result = drain(codec, muxer, info, trackIndex)
                if (result == -2) break
                trackIndex = result
            }

            if (muxerStarted) muxer.stop()
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { muxer.release() }
        }
    }

    /** @return -2 on encoder end of stream, otherwise the (updated) track index */
    private fun drain(codec: MediaCodec, muxer: MediaMuxer, info: MediaCodec.BufferInfo, trackIndex: Int): Int {
        var track = trackIndex
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return track
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
                continue
            }
            if (outIndex >= 0) {
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (!config && info.size > 0 && track >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    muxer.writeSampleData(track, buffer, info)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (eos) return -2
            }
        }
    }

    /** decode honoring EXIF orientation, center-crop-scale to the target size */
    private fun decodeUpright(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val rotated = try {
            val exif = ExifInterface(file.absolutePath)
            val degrees = when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (degrees != 0) {
                val m = Matrix().apply { postRotate(degrees.toFloat()) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            } else bmp
        } catch (_: Exception) {
            bmp
        }

        val out = centerCropScale(rotated, targetWidth, targetHeight)
        if (out !== rotated) rotated.recycle()
        return out
    }

    private fun centerCropScale(src: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (src.width == targetWidth && src.height == targetHeight) return src
        val scale = maxOf(
            targetWidth.toDouble() / src.width,
            targetHeight.toDouble() / src.height
        )
        val scaledW = (src.width * scale).roundToInt()
        val scaledH = (src.height * scale).roundToInt()
        val offX = (scaledW - targetWidth) / 2
        val offY = (scaledH - targetHeight) / 2
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val cropped = Bitmap.createBitmap(
            scaled, offX, offY,
            minOf(targetWidth, scaled.width), minOf(targetHeight, scaled.height), null, false
        )
        if (cropped !== scaled && cropped !== src) scaled.recycle()
        return cropped
    }

    /** ARGB to flexible YUV; handles both NV12 (pixelStride 2) and I420 (1) */
    private fun fillYuvFromBitmap(image: Image, bmp: Bitmap, width: Int, height: Int) {
        val planes = image.planes
        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer
        val yRow = planes[0].rowStride
        val yPix = planes[0].pixelStride
        val uRow = planes[1].rowStride
        val uPix = planes[1].pixelStride
        val vRow = planes[2].rowStride
        val vPix = planes[2].pixelStride

        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val rowOff = y * width
            val yIdx = y * yRow
            for (x in 0 until width) {
                val p = pixels[rowOff + x]
                val r = p shr 16 and 0xFF
                val g = p shr 8 and 0xFF
                val b = p and 0xFF

                val luma = 77 * r + 150 * g + 29 * b shr 8
                yBuf.put(yIdx + x * yPix, luma.toByte())

                if (y and 1 == 0 && x and 1 == 0) {
                    val chromaU = (-43 * r - 85 * g + 128 * b shr 8) + 128
                    val chromaV = (128 * r - 107 * g - 21 * b shr 8) + 128
                    uBuf.put(y / 2 * uRow + x / 2 * uPix, clamp(chromaU))
                    vBuf.put(y / 2 * vRow + x / 2 * vPix, clamp(chromaV))
                }
            }
        }
    }

    private fun clamp(v: Int): Byte = when {
        v < 0 -> 0.toByte()
        v > 255 -> 255.toByte()
        else -> v.toByte()
    }
}
