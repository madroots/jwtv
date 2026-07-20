package org.jw.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.jw.tv.ui.MainViewModel
import org.jw.tv.ui.VideoBrowserScreen
import org.jw.tv.ui.VideoPlayerScreen

/**
 * ARCH-2: TV Navigation stack using [NavHost] and [rememberNavController].
 * Handles route navigation, back-stack history, and back-press system integration.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val versionCode = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
        viewModel.checkForUpdates(versionCode)

        setContent {
            val navController = rememberNavController()
            val activeVideo = viewModel.activeVideo

            NavHost(
                navController = navController,
                startDestination = if (activeVideo != null) "player" else "browser"
            ) {
                composable("browser") {
                    VideoBrowserScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                composable("player") {
                    activeVideo?.let { video ->
                        VideoPlayerScreen(
                            video = video,
                            selectedResolution = viewModel.selectedResolution,
                            onBack = {
                                viewModel.activeVideo = null
                                navController.popBackStack("browser", inclusive = false)
                            }
                        )
                    } ?: run {
                        navController.popBackStack("browser", inclusive = false)
                    }
                }
            }
        }
    }
}
