package com.apkbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.apkbuilder.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val vm: BuildViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val projectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.pickProject(it) }
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.onFolderPicked(it) }
            val createDoc = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
            ) { vm.onCreateDocResult(it) }

            MaterialTheme(colorScheme = darkColorScheme(primary = vm.settings.accent)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppRoot(
                        vm = vm,
                        pickProject = { projectPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) },
                        pickFolder = { folderPicker.launch(null) },
                        createDoc = { createDoc.launch(it) }
                    )
                }
            }
        }
    }
}
