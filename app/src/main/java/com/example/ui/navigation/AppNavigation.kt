package com.example.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.GenerationMode
import com.example.ui.composition.CompositionScreen
import com.example.ui.composition.CompositionViewModel
import com.example.ui.generate.GenerateScreen
import com.example.ui.generate.GenerateViewModel
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    generateViewModel: GenerateViewModel = viewModel(),
    compositionViewModel: CompositionViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf(Screen.GENERATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Listen to toasts/snackbars from ViewModels
    LaunchedEffect(Unit) {
        launch {
            generateViewModel.toastEvents.collectLatest { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
        launch {
            compositionViewModel.toastEvents.collectLatest { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
        launch {
            historyViewModel.toastEvents.collectLatest { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
        launch {
            settingsViewModel.toastEvents.collectLatest { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                Screen.entries.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("nav_${screen.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            Screen.GENERATE -> {
                GenerateScreen(
                    viewModel = generateViewModel,
                    onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                    onNavigateToComposition = { currentScreen = Screen.COMPOSITION },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.COMPOSITION -> {
                CompositionScreen(
                    viewModel = compositionViewModel,
                    onSendToImg2img = { renderedUri ->
                        generateViewModel.setMode(GenerationMode.IMAGE_TO_IMAGE)
                        generateViewModel.clearAllSelectedImages()
                        generateViewModel.setSourceImage(renderedUri)
                        currentScreen = Screen.GENERATE
                        scope.launch {
                            snackbarHostState.showSnackbar("Composition loaded into img2img")
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.HISTORY -> {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onReuseGeneration = { item ->
                        generateViewModel.reuseGeneration(item)
                        currentScreen = Screen.GENERATE
                        scope.launch {
                            snackbarHostState.showSnackbar("Prompt and parameters restored")
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
