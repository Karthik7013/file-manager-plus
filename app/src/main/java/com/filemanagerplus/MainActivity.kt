package com.filemanagerplus

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import com.filemanagerplus.ui.FileManagerRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        setContent {
            var useDark by remember { mutableStateOf(isSystemInDarkTheme()) }
            MaterialTheme(colorScheme = if (useDark) darkColorScheme() else lightColorScheme()) {
                FileManagerRoot(isTv = isTv, onToggleTheme = { useDark = !useDark }, isDark = useDark)
            }
        }
    }
}
