package de.rogallab.mobile.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest

@Composable
fun RequestPermissionsWithRationale(
   onGranted: () -> Unit,
   onDenied: () -> Unit
) {
   val context = LocalContext.current
   val activity = remember { context.findActivity() }

   val permissions = remember {
      arrayOf(
         Manifest.permission.CAMERA,
         Manifest.permission.RECORD_AUDIO
      )
   }

   var showRationale by remember { mutableStateOf(false) }
   var showGoToSettings by remember { mutableStateOf(false) }

   val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
   ) { result ->
      val allGranted = result.values.all { it }
      if (allGranted) {
         onGranted()
      } else {
         // Prüfen, ob jetzt „Nicht mehr fragen“ aktiv ist
         val permanentlyDenied = permissions.any { p ->
            ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED &&
               activity?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, p) } == true
         }
         if (permanentlyDenied) {
            showGoToSettings = true
         } else {
            onDenied()
         }
      }
   }

   LaunchedEffect(Unit) {
      val allGranted = permissions.all { p ->
         ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
      }
      if (allGranted) {
         onGranted()
      } else {
         // Wenn für mindestens eine Permission eine Begründung nötig ist → Rationale zeigen
         val needsRationale = permissions.any { p ->
            activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, p) } == true
         }
         if (needsRationale) {
            showRationale = true
         } else {
            launcher.launch(permissions)
         }
      }
   }

   if (showRationale) {
      AlertDialog(
         onDismissRequest = { showRationale = false; onDenied() },
         title = { Text("Zugriff erforderlich") },
         text = {
            Text("Die App benötigt Kamera- und Mikrofonzugriff, um Video/Audio aufzunehmen. " +
               "Ohne diese Berechtigungen funktioniert die Aufnahme nicht.")
         },
         confirmButton = {
            TextButton(onClick = {
               showRationale = false
               launcher.launch(permissions)
            }) { Text("Verstanden, fortfahren") }
         },
         dismissButton = {
            TextButton(onClick = { showRationale = false; onDenied() }) { Text("Abbrechen") }
         }
      )
   }

   if (showGoToSettings) {
      AlertDialog(
         onDismissRequest = { showGoToSettings = false; onDenied() },
         title = { Text("Berechtigung dauerhaft verweigert") },
         text = {
            Text("Du hast mindestens eine Berechtigung dauerhaft verweigert. " +
               "Bitte erlaube Kamera und Mikrofon in den App-Einstellungen.")
         },
         confirmButton = {
            TextButton(onClick = {
               showGoToSettings = false
               context.openAppSettings()
            }) { Text("Zu den Einstellungen") }
         },
         dismissButton = {
            TextButton(onClick = { showGoToSettings = false; onDenied() }) { Text("Später") }
         }
      )
   }
}

// --- Hilfsfunktionen ---

private fun Context.findActivity(): Activity? = when (this) {
   is Activity -> this
   is ContextWrapper -> baseContext.findActivity()
   else -> null
}

private fun Context.openAppSettings() {
   val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", packageName, null)
   ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   startActivity(intent)
}
