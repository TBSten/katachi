package com.example.kmp.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.example.kmp.data.UserRepositoryImpl
import com.example.kmp.feature.home.HomeScreen
import com.example.kmp.feature.home.HomeViewModel

/**
 * Entry point of the Android app. Plain [Activity] and a [TextView]: the sample depends on
 * neither Compose nor AndroidX, so that a build only exercises AGP, Kotlin and katachi.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = HomeScreen(HomeViewModel(UserRepositoryImpl()))
        setContentView(TextView(this).apply { text = screen.render() })
    }
}
