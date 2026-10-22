package com.chronolapse.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chronolapse.app.ui.library.LibraryScreen
import com.chronolapse.app.ui.settings.SettingsScreen
import com.chronolapse.app.ui.theme.ChronoTheme
import com.chronolapse.app.ui.camera.CameraScreen
import com.chronolapse.app.ui.viewer.ViewerScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChronoTheme {
                AppNavHost()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    // one time prompts, same policy as the previous version
    var granted by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result[Manifest.permission.CAMERA] == true }

    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        launcher.launch(wanted.toTypedArray())
        // only prompt when the exemption is not already granted
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE)
            as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + context.packageName))
                )
            }
        }
    }

    NavHost(nav, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                granted = granted,
                onOpenLibrary = { nav.navigate("library") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable("library") {
            LibraryScreen(
                onBack = { nav.popBackStack() },
                onOpenProject = { dir, session, title ->
                    nav.navigate("viewer/photos?dir=${Uri.encode(dir)}&session=${Uri.encode(session)}&title=${Uri.encode(title)}")
                },
                onOpenVideo = { uri, title, duration ->
                    nav.navigate("viewer/video?uri=${Uri.encode(uri)}&title=${Uri.encode(title)}&duration=$duration")
                },
            )
        }
        composable(
            route = "viewer/photos?dir={dir}&session={session}&title={title}",
            arguments = listOf(
                navArgument("dir") { type = NavType.StringType },
                navArgument("session") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { entry ->
            ViewerScreen(
                type = "photos",
                dir = entry.arguments?.getString("dir") ?: "",
                session = entry.arguments?.getString("session") ?: "",
                videoUri = null,
                title = entry.arguments?.getString("title") ?: "",
                durationMs = 0,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "viewer/video?uri={uri}&title={title}&duration={duration}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("duration") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            ViewerScreen(
                type = "video",
                dir = null,
                session = null,
                videoUri = entry.arguments?.getString("uri"),
                title = entry.arguments?.getString("title") ?: "",
                durationMs = entry.arguments?.getLong("duration") ?: 0L,
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
