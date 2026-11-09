package app.brano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brano.ui.AppViewModel
import app.brano.ui.navigation.BranoRoot
import app.brano.ui.theme.BranoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val seed by viewModel.seedColor.collectAsStateWithLifecycle()
            BranoTheme(settings, seed) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BranoRoot(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind the playback service (ExoPlayer + MediaSession setup on the main thread) only after
        // the first frame, so it does not delay it; the mini-player appears a frame later.
        lifecycleScope.launch(AndroidUiDispatcher.Main) {
            withFrameNanos { }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.connect()
        }
    }

    override fun onStop() {
        viewModel.disconnect()
        super.onStop()
    }
}
