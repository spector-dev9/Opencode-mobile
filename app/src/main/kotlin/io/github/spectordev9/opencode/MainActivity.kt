package io.github.spectordev9.opencode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.spectordev9.opencode.ui.OpenCodeMobileApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = AppDependencies.forBuild(applicationContext)
        setContent {
            OpenCodeMobileApp(dependencies)
        }
    }
}
