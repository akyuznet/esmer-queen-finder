package com.esmer.queenfinder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.esmer.queenfinder.ui.camera.CameraScreen
import com.esmer.queenfinder.ui.settings.SettingsScreen
import com.esmer.queenfinder.ui.theme.ESMERQUEENFINDERTheme

object Routes {
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Hands are busy with the frame; never let the screen sleep mid-inspection.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ESMERQUEENFINDERTheme {
                QueenFinderNavHost()
            }
        }
    }
}

@Composable
private fun QueenFinderNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            CameraScreen(onOpenSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
