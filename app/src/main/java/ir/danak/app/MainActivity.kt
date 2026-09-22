package ir.danak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ir.danak.app.ui.DanakApp
import ir.danak.app.ui.DanakViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DanakViewModel by viewModels { DanakViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays up until saved state is read, so a returning user never sees a
        // flash of onboarding before their feed.
        installSplashScreen().setKeepOnScreenCondition { !viewModel.state.value.isLoaded }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { DanakApp(viewModel) }
    }
}
