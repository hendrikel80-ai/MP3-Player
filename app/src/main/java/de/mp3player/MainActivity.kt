package de.mp3player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.mp3player.ui.Mp3PlayerApp
import de.mp3player.ui.theme.Mp3PlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Mp3PlayerTheme {
                Mp3PlayerApp()
            }
        }
    }
}
