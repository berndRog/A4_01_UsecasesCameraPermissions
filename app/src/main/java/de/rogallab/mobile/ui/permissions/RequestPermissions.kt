package de.rogallab.mobile.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun RequestCameraWithLocationPermissions(
   needBackgroundLocation: Boolean = false,
   onAllGranted: () -> Unit,
   onDenied: (permanentlyDenied: Boolean) -> Unit
) {
   val context = LocalContext.current

   val permissions = remember {
      buildList {
         add(Manifest.permission.CAMERA)
         add(Manifest.permission.RECORD_AUDIO)
         add(Manifest.permission.ACCESS_FINE_LOCATION)
         add(Manifest.permission.ACCESS_COARSE_LOCATION)
         //if (needBackgroundLocation) {
         //   add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
         //}
      }.toTypedArray()
   }

   var showSettingsDialog by remember { mutableStateOf(false) }

   val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
   ) { result ->
      val allGranted = permissions.all { permission ->
         result[permission] == true || context.isGranted(permission)
      }

      if (allGranted) {
         onAllGranted()
      } else {
         val hasAnyPermission = permissions.any { context.isGranted(it) }
         if (hasAnyPermission) {
            onDenied(false)
         } else {
            showSettingsDialog = true
         }
      }
   }

   LaunchedEffect(Unit) {
      val allGranted = permissions.all { context.isGranted(it) }

      if (allGranted) {
         onAllGranted()
      } else {
         launcher.launch(permissions)
      }
   }

   if (showSettingsDialog) {
      AlertDialog(
         onDismissRequest = {
            showSettingsDialog = false
            onDenied(true)
         },
         title = { Text("Permissions Required") },
         text = {
            Text(
               "Camera and Location access needed for geotagged photos. " +
                  "Please enable in app settings."
            )
         },
         confirmButton = {
            TextButton(onClick = {
               showSettingsDialog = false
               context.openAppSettings()
            }) {
               Text("Open Settings")
            }
         },
         dismissButton = {
            TextButton(onClick = {
               showSettingsDialog = false
               onDenied(true)
            }) {
               Text("Cancel")
            }
         }
      )
   }
}

private fun Context.isGranted(permission: String): Boolean =
   ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
   val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", packageName, null)
   ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   startActivity(intent)
}