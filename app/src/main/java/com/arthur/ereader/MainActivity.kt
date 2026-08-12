package com.arthur.ereader

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.arthur.ereader.navigation.AppNavigation
import com.arthur.ereader.reader.common.ReaderSettingsViewModel
import com.arthur.ereader.ui.theme.EreaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: ReaderSettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.globalSettings.collectAsStateWithLifecycle()
            EreaderTheme(settings.appTheme) {
                AppNavigation()
            }
        }
    }
}
