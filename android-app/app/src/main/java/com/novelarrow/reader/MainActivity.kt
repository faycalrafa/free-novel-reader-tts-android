package com.novelarrow.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelarrow.reader.theme.NovelArrowReaderTheme
import com.novelarrow.reader.ui.reader.ReaderScreen
import com.novelarrow.reader.ui.reader.ReaderViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: ReaderViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()

            NovelArrowReaderTheme(themeMode = uiState.settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ReaderScreen(viewModel = viewModel)
                }
            }
        }
    }
}
