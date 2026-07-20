package org.jw.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.jw.tv.ui.MainViewModel
import org.jw.tv.api.MediatorClient
import org.jw.tv.ui.VideoBrowserScreen
import org.jw.tv.ui.VideoPlayerScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MediatorClient.init(applicationContext)
        val versionCode = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
        viewModel.checkForUpdates(versionCode)

        setContent {
            val activeVideo = viewModel.activeVideo
            if (activeVideo != null) {
                VideoPlayerScreen(
                    video = activeVideo,
                    selectedResolution = viewModel.selectedResolution,
                    onBack = { viewModel.activeVideo = null }
                )
            } else {
                VideoBrowserScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
