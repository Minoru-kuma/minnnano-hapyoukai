package com.example.minnanohappyokai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.minnanohappyokai.data.AppContainer
import com.example.minnanohappyokai.ui.RecitalApp
import com.example.minnanohappyokai.ui.RecitalViewModel
import com.example.minnanohappyokai.ui.theme.MinnanoHappyokaiTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }
    private val recitalViewModel by lazy {
        ViewModelProvider(this, RecitalViewModel.Factory(container.repository))[RecitalViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MinnanoHappyokaiTheme {
                RecitalApp(recitalViewModel)
            }
        }
    }
}
