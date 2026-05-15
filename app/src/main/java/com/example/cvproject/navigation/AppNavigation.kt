package com.example.cvproject.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cvproject.data.preferences.PreferencesManager
import com.example.cvproject.data.repository.TripRepository
import com.example.cvproject.ui.screens.DetectionScreen
import com.example.cvproject.ui.screens.HomeScreen
import com.example.cvproject.ui.screens.SettingsScreen
import com.example.cvproject.ui.screens.SplashScreen
import com.example.cvproject.ui.screens.TripLogScreen
import com.example.cvproject.viewmodel.MainViewModel
import com.example.cvproject.viewmodel.SettingsViewModel
import com.example.cvproject.viewmodel.TripViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    tripRepository: TripRepository,
    preferencesManager: PreferencesManager,
) {
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(tripRepository))
    val tripViewModel: TripViewModel = viewModel(factory = TripViewModel.factory(tripRepository))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(preferencesManager))
    LaunchedEffect(viewModel, preferencesManager) {
        viewModel.bindPreferences(preferencesManager)
    }
    DisposableEffect(navController) {
        val listener =
            androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                Log.d(
                    "Navigation",
                    "Navigation destination changed: ${destination.route ?: "unknown"}"
                )
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onNavigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.Detection.route) {
            DetectionScreen(navController = navController, viewModel = viewModel)
        }
        composable(Screen.TripLog.route) {
            TripLogScreen(viewModel = tripViewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = settingsViewModel)
        }
    }
}
