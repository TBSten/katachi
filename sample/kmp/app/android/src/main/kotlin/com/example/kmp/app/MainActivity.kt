package com.example.kmp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.kmp.data.user.UserRepositoryImpl

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = UserRepositoryImpl()
        setContent {
            AppRoot(repository = repository)
        }
    }
}
