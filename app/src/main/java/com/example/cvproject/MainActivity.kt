package com.example.cvproject

import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.cvproject.navigation.AppNavigation
import com.example.cvproject.ui.theme.CvprojectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("AppDebug", "MainActivity onCreate started")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CvProjectApp
        setContent {
            val themeSelection by app.preferencesManager.themeSelection.collectAsState(initial = "dark")
            CvprojectTheme(darkTheme = themeSelection != "light") {
                val navController = rememberNavController()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        navController = navController,
                        tripRepository = app.tripRepository,
                        preferencesManager = app.preferencesManager,
                    )
                }
            }
        }
    }
}
