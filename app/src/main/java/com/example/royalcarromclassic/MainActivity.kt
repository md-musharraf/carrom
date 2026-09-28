package com.example.royalcarromclassic

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.royalcarromclassic.theme.RoyalCarromClassicTheme
import com.example.royalcarromclassic.ui.MainGameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The game is always dark: light system bar icons over a transparent, edge-to-edge window.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            RoyalCarromClassicTheme {
                MainGameScreen()
            }
        }
    }
}
