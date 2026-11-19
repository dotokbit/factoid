package com.example.factoid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.factoid.data.repository.FactoidRepository
import com.example.factoid.ui.MainViewModel
import com.example.factoid.ui.MainViewModelFactory
import com.example.factoid.ui.navigation.AppNavigation
import com.example.factoid.ui.theme.FactoidTheme

class MainActivity : ComponentActivity() {

    private val repository: FactoidRepository by lazy {
        FactoidRepository.create(applicationContext)
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FactoidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
