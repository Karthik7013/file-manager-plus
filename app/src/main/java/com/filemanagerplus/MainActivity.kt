package com.filemanagerplus

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import com.filemanagerplus.data.StorageRepository
import com.filemanagerplus.ui.FileManagerRoot

class MainActivity : ComponentActivity() {

    private val requestAllFilesAccess =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            // Permission result is checked by re-evaluating Environment.isExternalStorageManager()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var useDark by remember { mutableStateOf(systemDark) }
            val hasStorageAccess by remember {
                mutableStateOf(StorageRepository.hasAllFilesAccess(this@MainActivity))
            }
            MaterialTheme(colorScheme = if (useDark) darkColorScheme() else lightColorScheme()) {
                if (!hasStorageAccess) {
                    PermissionScreen(
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                    .addCategory(Intent.CATEGORY_DEFAULT)
                                    .setData(Uri.parse("package:${packageName}"))
                                requestAllFilesAccess.launch(intent)
                            }
                        },
                        granted = { }
                    )
                } else {
                    FileManagerRoot(
                        isTv = isTv,
                        onToggleTheme = { useDark = !useDark },
                        isDark = useDark
                    )
                }
            }
        }
    }
}
