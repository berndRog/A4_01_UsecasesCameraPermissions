// PermissionHelpers.kt — single-file, compilable version
// Package: adjust if needed
package de.rogallab.mobile.ui.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
// Geo helpers
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Data model for a permission request.
 */
data class PermissionRequestConfig(
   val permissions: Array<String>,
   val onAllGranted: () -> Unit,
   val onDenied: (denied: List<String>, permanently: Boolean) -> Unit = { _, _ -> },
   val showRationale: (permission: String) -> Unit = {},
)

/**
 * Core JIT permission requester. Pass only DANGEROUS permissions.
 */
@Composable
fun RequirePermissions(config: PermissionRequestConfig) {
   val ctx = LocalContext.current

   // compute missing at composition time
   val missing = remember(config.permissions.joinToString("|")) {
      config.permissions.filter {
         ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
      }
   }

   val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
   ) { result ->
      val denied = result.filterValues { granted -> !granted }.keys.toList()
      if (denied.isEmpty()) {
         config.onAllGranted()
      } else {
         val permanently = denied.any { perm -> !ctx.shouldShowRationale(perm) }
         config.onDenied(denied, permanently)
         denied.forEach { perm -> if (ctx.shouldShowRationale(perm)) config.showRationale(perm) }
      }
   }

   LaunchedEffect(missing) {
      if (missing.isEmpty()) config.onAllGranted()
      else launcher.launch(missing.toTypedArray())
   }
}

// ────────────────────────────────────────────────────────────
// Convenience wrappers mapped to your manifest and use-cases
// ────────────────────────────────────────────────────────────

@Composable
fun RequireCamera(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = arrayOf(Manifest.permission.CAMERA),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

@Composable
fun RequireAudioRecord(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = arrayOf(Manifest.permission.RECORD_AUDIO),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

@Composable
fun RequirePhotosRead(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = mediaReadImagesPerms(),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

@Composable
fun RequireVideosRead(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = mediaReadVideosPerms(),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

@Composable
fun RequireAudioRead(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = mediaReadAudioPerms(),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

/** Optional: unredacted EXIF GPS for photos */
@Composable
fun RequireAccessMediaLocation(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = arrayOf(Manifest.permission.ACCESS_MEDIA_LOCATION),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

@Composable
fun RequireLocationWhileInUse(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

/**
 * Background location must be requested after while‑in‑use was granted.
 * Policy-compliant, API 29+ only.
 */
@Composable
fun RequireBackgroundLocation(
   onGranted: () -> Unit,
   onDenied: (permanently: Boolean) -> Unit = {}
) {
   if (Build.VERSION.SDK_INT < 29) {
      // Before Android 10 there is no separate background permission
      RequireLocationWhileInUse(onGranted = onGranted, onDenied = onDenied)
      return
   }

   var step by remember { mutableStateOf(0) } // 0: ask foreground, 1: ask background

   when (step) {
      0 -> RequireLocationWhileInUse(
         onGranted = { step = 1 },
         onDenied = { permDenied -> onDenied(permDenied) }
      )
      1 -> RequirePermissions(
         PermissionRequestConfig(
            permissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            onAllGranted = onGranted,
            onDenied = { _, permanently -> onDenied(permanently) }
         )
      )
   }
}

@Composable
fun RequireNotifications(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   if (Build.VERSION.SDK_INT >= 33) {
      RequirePermissions(
         PermissionRequestConfig(
            permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            onAllGranted = onGranted,
            onDenied = { _, permanently -> onDenied(permanently) }
         )
      )
   } else {
      SideEffect { onGranted() }
   }
}

/** Combined helpers **/
@Composable
fun RequireCameraWithAudio(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
         ),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

@Composable
fun RequireCameraAudioWithLocation(onGranted: () -> Unit, onDenied: (Boolean) -> Unit = {}) {
   RequirePermissions(
      PermissionRequestConfig(
         permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
         ),
         onAllGranted = onGranted,
         onDenied = { _, permanently -> onDenied(permanently) }
      )
   )
}

/**
 * Ensure prerequisites to start a Location Foreground Service (FGS):
 * - On Android 13+ request POST_NOTIFICATIONS before posting the ongoing notification
 * - Ensure while-in-use location is granted
 * (Manifest must also declare FOREGROUND_SERVICE + foregroundServiceType="location")
 */
@Composable
fun EnsureLocationFgServiceReady(
   onReady: () -> Unit,
   onDenied: (permanently: Boolean) -> Unit = {}
) {
   var step by remember { mutableStateOf(0) } // 0: notifications, 1: location

   when (step) {
      0 -> if (Build.VERSION.SDK_INT >= 33) {
         RequireNotifications(
            onGranted = { step = 1 },
            onDenied = { perm -> onDenied(perm) }
         )
      } else step = 1
      1 -> RequireLocationWhileInUse(
         onGranted = onReady,
         onDenied = onDenied
      )
   }
}

// ────────────────────────────────────────────────────────────
// Platform-aware permission arrays
// ────────────────────────────────────────────────────────────

fun mediaReadImagesPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
   else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun mediaReadVideosPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
   else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun mediaReadAudioPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
   else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

// Wi‑Fi scan helper (only if you add NEARBY_WIFI_DEVICES in manifest for API 33+)
fun wifiScanPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
   else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

// BLE scan helper (not strictly required by your list but useful)
fun btScanPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 31)
      arrayOf(Manifest.permission.BLUETOOTH_SCAN)
   else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)

// ────────────────────────────────────────────────────────────
// Geotagging helpers (while‑in‑use) — optional section
// ────────────────────────────────────────────────────────────
@Composable
fun GeoTagGate(
   onLocationReady: (lat: Double, lon: Double) -> Unit,
   onDenied: (permanently: Boolean) -> Unit = {},
   content: @Composable () -> Unit
) {
   var ask by remember { mutableStateOf(false) }
   var locGranted by remember { mutableStateOf(false) }

   val context = LocalContext.current

   if (locGranted) {
      content()
   } else {
      Button(onClick = { ask = true }) { Text("Enable location for geotagging") }
      if (ask) {
         RequireLocationWhileInUse(
            onGranted = {
               ask = false
               locGranted = true
               requestSingleFix(context) { lat, lon ->
                  onLocationReady(lat, lon)
               }
            },
            onDenied = { permanently ->
               ask = false
               onDenied(permanently)
            }
         )
      }
   }
}

@SuppressLint("MissingPermission")
private fun requestSingleFix(
   context: Context,
   onFix: (Double, Double) -> Unit
) {
   val fused = LocationServices.getFusedLocationProviderClient(context)
   fused.lastLocation.addOnSuccessListener { last ->
      if (last != null) {
         onFix(last.latitude, last.longitude)
         return@addOnSuccessListener
      }
      val req = CurrentLocationRequest.Builder()
         .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
         .setMaxUpdateAgeMillis(5_000)
         .build()
      fused.getCurrentLocation(req, CancellationTokenSource().token)
         .addOnSuccessListener { loc ->
            if (loc != null) onFix(loc.latitude, loc.longitude)
         }
   }
}

// ────────────────────────────────────────────────────────────
// Simple rationale dialog (optional UI piece)
// ────────────────────────────────────────────────────────────
@Composable
fun PermissionRationaleDialog(
   permission: String,
   message: String,
   onConfirm: () -> Unit,
   onDismiss: () -> Unit,
) {
   AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Permission required") },
      text = { Text(message) },
      confirmButton = { Button(onClick = onConfirm) { Text("Continue") } },
      dismissButton = { Button(onClick = onDismiss) { Text("Not now") } },
   )
}

// ────────────────────────────────────────────────────────────
// Example entries (can be removed; they compile as-is)
// ────────────────────────────────────────────────────────────
@Composable
fun CameraEntry(onReady: () -> Unit) {
   var ask by remember { mutableStateOf(false) }
   if (ask) {
      RequireCamera(
         onGranted = { ask = false; onReady() },
         onDenied = { _ -> ask = false }
      )
   }
   Button(onClick = { ask = true }) { Text("Open camera") }
}

@Composable
fun GalleryEntry(onReady: () -> Unit) {
   var ask by remember { mutableStateOf(false) }
   if (ask) {
      RequirePhotosRead(
         onGranted = { ask = false; onReady() },
         onDenied = { _ -> ask = false }
      )
   }
   Button(onClick = { ask = true }) { Text("Open gallery") }
}

@Composable
fun VideoRecordEntry(onReady: () -> Unit) {
   var ask by remember { mutableStateOf(false) }
   if (ask) {
      RequireCameraWithAudio(
         onGranted = { ask = false; onReady() },
         onDenied = { _ -> ask = false }
      )
   }
   Button(onClick = { ask = true }) { Text("Start recording") }
}

@Composable
fun StartLocationTrackingEntry(onReady: () -> Unit) {
   var ask by remember { mutableStateOf(false) }
   if (ask) {
      EnsureLocationFgServiceReady(
         onReady = { ask = false; onReady() },
         onDenied = { _ -> ask = false }
      )
   }
   Button(onClick = { ask = true }) { Text("Start location tracking (FGS)") }
}

@Composable
fun UpgradeToBackgroundLocationEntry(onReady: () -> Unit) {
   var ask by remember { mutableStateOf(false) }
   if (ask) {
      RequireBackgroundLocation(
         onGranted = { ask = false; onReady() },
         onDenied = { _ -> ask = false }
      )
   }
   Button(onClick = { ask = true }) { Text("Allow background location") }
}

// ────────────────────────────────────────────────────────────
// Small helpers
// ────────────────────────────────────────────────────────────
fun Context.shouldShowRationale(permission: String): Boolean =
   (this as? Activity)?.let { activity ->
      androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
   } ?: false

fun Context.openAppSettings() {
   val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   }
   try { startActivity(intent) } catch (_: ActivityNotFoundException) { /* ignore */ }
}