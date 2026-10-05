package com.example.kmp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.kmp.data.user.UserRepositoryImpl

/**
 * Entry point of the Android app.
 *
 * It owns nothing but the wiring: the repository implementation and [AppRoot], which is the
 * part that could be shared with an iOS `ComposeUIViewController` once app/ios grows one.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = UserRepositoryImpl()
        setContent {
            AppRoot(repository = repository)
        }
    }
}
