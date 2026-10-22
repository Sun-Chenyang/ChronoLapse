package com.chronolapse.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.chronolapse.app.data.MediaRepository
import com.chronolapse.app.ui.theme.GlassFill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** asynchronously decoded MediaStore thumbnail */
@Composable
fun MediaThumbImage(
    uri: Uri,
    video: Boolean,
    size: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val ctx = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = MediaRepository.loadThumbnail(ctx, uri, video, size)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(GlassFill))
    }
}

/** run a block on the IO dispatcher inside produceState-style loaders */
suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
