package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.GraphitonTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GraphitonApplication
        val preferencesManager = app.preferencesManager

        setContent {
            val prefs by preferencesManager.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.local.AppPreferences()
            )

            val isDark = when (prefs.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            GraphitonTheme(darkTheme = isDark) {
                AppNavigation()
            }
        }
    }
}
