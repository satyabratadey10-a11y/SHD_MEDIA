package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.PrismGramTheme
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Mandatory App Check intent extra wiring before setContent, ViewModels or Firebase operations
        configureAppCheck(this, intent)

        setContent {
            PrismGramTheme {
                AppNavigation()
            }
        }
    }
}
