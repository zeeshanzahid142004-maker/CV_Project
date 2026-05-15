package com.example.cvproject.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object Detection : Screen("detection")
    data object TripLog : Screen("trip_log")
    data object Settings : Screen("settings")
}
