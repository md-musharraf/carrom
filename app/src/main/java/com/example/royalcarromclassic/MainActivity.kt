package com.example.royalcarromclassic

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.royalcarromclassic.online.AndroidSocialAuth
import com.example.royalcarromclassic.online.SocialAuthProvider
import com.example.royalcarromclassic.theme.RoyalCarromClassicTheme
import com.example.royalcarromclassic.ui.CarromApp
import com.example.royalcarromclassic.ui.PlatformBridge

class MainActivity : ComponentActivity() {

    private val platform = object : PlatformBridge {
        override val socialAuth: SocialAuthProvider by lazy { AndroidSocialAuth(this@MainActivity) }

        override fun shareText(text: String) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(send, null))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The game is always dark: light system bar icons over a transparent, edge-to-edge window.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            RoyalCarromClassicTheme {
                CarromApp(platform = platform)
            }
        }
    }
}
