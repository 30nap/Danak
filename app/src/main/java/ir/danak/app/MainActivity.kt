package ir.danak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.danak.app.ui.DanakApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Danak draws its own artwork behind the system bars; DanakTheme keeps the bar
        // icon tint in step with the active theme.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { DanakApp() }
    }
}
