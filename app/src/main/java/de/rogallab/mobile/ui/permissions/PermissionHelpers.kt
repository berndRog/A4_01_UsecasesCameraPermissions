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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Data model describing a permission request batch.
 *
 * @param permissions list of dangerous permissions to request at runtime
 * @param onAllGranted called once when all permissions in the batch are granted
 * @param onDenied called when at least one permission is denied
 * @param showRationale callback for showing an explanation UI for a single permission
 */
data class PermissionRequestConfig(
   val permissions: Array<String>,
   val onAllGranted: () -> Unit,
   val onDenied: (denied: List<String>, permanently: Boolean) -> Unit = { _, _ -> },
   val showRationale: (permission: String) -> Unit = {},
)

/**
 * Core JIT (just-in-time) permission requester.
 *
 * - Takes a batch of dangerous permissions.
 * - Checks which ones are missing.
 * - If none are missing, immediately calls onAllGranted().
 * - Otherwise launches the system permission dialog and dispatches the result.
 *
 * IMPORTANT: This composable itself does not show UI; it just coordinates the request.
 * Rationale UI must be implemented by the caller via showRationale() or external state.
 */
@Composable
fun RequirePermissions(config: PermissionRequestConfig) {
   val ctx = LocalContext.current

   // Compute missing permissions once per config.permissions key.
   // We use remember so the list is stable during one permission flow.
   val missing = remember(config.permissions.joinToString("|")) {
      config.permissions.filter {
         ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
      }
   }

   // Launcher for RequestMultiplePermissions()
   val launcher = rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
   ) { result ->
      val denied = result.filterValues { granted -> !granted }.keys.toList()
      if (denied.isEmpty()) {
         // All permissions granted
         config.onAllGranted()
      } else {
         // At least one denied
         // "Permanently" here means: at least one denied permission has
         // shouldShowRequestPermissionRationale() == false after the request.
         val permanently = denied.any { perm -> !ctx.shouldShowRationale(perm) }
         config.onDenied(denied, permanently)

         // For each denied permission where a rationale is appropriate,
         // let the caller show an explanation (dialog, snackbar, etc.).
         denied.forEach { perm ->
            if (ctx.shouldShowRationale(perm)) {
               config.showRationale(perm)
            }
         }
      }
   }

   // Side-effect that actually triggers the request once.
   LaunchedEffect(missing) {
      if (missing.isEmpty()) {
         // Everything already granted → treat as success
         config.onAllGranted()
      } else {
         // Launch system permission dialog for all missing permissions
         launcher.launch(missing.toTypedArray())
      }
   }
}

// ────────────────────────────────────────────────────────────
// Convenience wrappers mapped to your manifest and use cases
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

/**
 * Optional: unredacted EXIF GPS data for photos.
 */
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
 * Request background location *after* while-in-use was granted.
 *
 * - For API < 29, background location does not exist as a separate permission.
 *   In that case we just ensure while-in-use location.
 * - For API 29+, step 0 requests while-in-use, step 1 requests ACCESS_BACKGROUND_LOCATION.
 */
@Composable
fun RequireBackgroundLocation(
   onGranted: () -> Unit,
   onDenied: (permanently: Boolean) -> Unit = {}
) {
   if (Build.VERSION.SDK_INT < 29) {
      // Before Android 10 there is no separate background permission.
      RequireLocationWhileInUse(onGranted = onGranted, onDenied = onDenied)
      return
   }

   var step by remember { mutableIntStateOf(0) } // 0: ask foreground, 1: ask background

   when (step) {
      0 -> RequireLocationWhileInUse(
         onGranted = { step = 1 },
         onDenied = { permanently -> onDenied(permanently) }
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
      // Before Android 13, notification permission is implicitly granted.
      SideEffect { onGranted() }
   }
}

/**
 * Combined convenience helpers.
 */

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
 *
 * - On Android 13+ request POST_NOTIFICATIONS before posting the ongoing notification
 * - Ensure while-in-use location is granted
 *
 * NOTE:
 * The manifest must also declare:
 *   - android.permission.FOREGROUND_SERVICE
 *   - foregroundServiceType="location" on the service
 */
@Composable
fun EnsureLocationFgServiceReady(
   onReady: () -> Unit,
   onDenied: (permanently: Boolean) -> Unit = {}
) {
   var step by remember { mutableStateOf(0) } // 0: notifications, 1: location

   when (step) {
      0 ->
         if (Build.VERSION.SDK_INT >= 33) {
            RequireNotifications(
               onGranted = { step = 1 },
               onDenied = { permanently -> onDenied(permanently) }
            )
         } else {
            // On older platforms we can skip the notification permission step
            step = 1
         }

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
   else
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun mediaReadVideosPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
   else
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun mediaReadAudioPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
   else
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

// Wi-Fi scan helper (requires NEARBY_WIFI_DEVICES in manifest for API 33+)
fun wifiScanPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 33)
      arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
   else
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

// BLE scan helper (typical pattern for modern vs legacy BLUETOOTH permissions)
fun btScanPerms(): Array<String> =
   if (Build.VERSION.SDK_INT >= 31)
      arrayOf(Manifest.permission.BLUETOOTH_SCAN)
   else
      arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)

// ────────────────────────────────────────────────────────────
// Geotagging helpers (while-in-use) — optional section
// ────────────────────────────────────────────────────────────

/**
 * Small gate composable:
 * - Shows a button to enable geotagging.
 * - When clicked, requests while-in-use location permission.
 * - On success, requests one single location fix and calls onLocationReady().
 * - When permission is granted, the content() composable is shown.
 */
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
      // Permission already granted → show main content
      content()
   } else {
      // Simple trigger UI
      Button(onClick = { ask = true }) {
         Text("Enable location for geotagging")
      }
      if (ask) {
         RequireLocationWhileInUse(
            onGranted = {
               ask = false
               locGranted = true
               // Once permission is granted, request a single location fix.
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

/**
 * Requests a single best-effort location fix.
 *
 * Caller must ensure that location permission has already been granted.
 * This method first tries lastLocation, then falls back to getCurrentLocation().
 */
@SuppressLint("MissingPermission")
private fun requestSingleFix(
   context: Context,
   onFix: (Double, Double) -> Unit
) {
   val fused = LocationServices.getFusedLocationProviderClient(context)

   // First try last known location (fast, may be stale but usually good enough for EXIF).
   fused.lastLocation.addOnSuccessListener { last ->
      if (last != null) {
         onFix(last.latitude, last.longitude)
         return@addOnSuccessListener
      }

      // If no last location is available, request a fresh current location.
      val req = CurrentLocationRequest.Builder()
         .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
         .setMaxUpdateAgeMillis(5_000)
         .build()

      fused.getCurrentLocation(req, CancellationTokenSource().token)
         .addOnSuccessListener { loc ->
            if (loc != null) {
               onFix(loc.latitude, loc.longitude)
            }
         }
   }
}

// ────────────────────────────────────────────────────────────
// Simple rationale dialog (optional UI piece)
// ────────────────────────────────────────────────────────────

/**
 * Generic permission rationale dialog.
 *
 * Use this together with PermissionRequestConfig.showRationale by
 * storing the current "permissionNeedingRationale" in state.
 */
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
// Example entry composables (can be removed in production)
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

/**
 * Wrapper for ActivityCompat.shouldShowRequestPermissionRationale().
 *
 * Returns false if the Context is not an Activity or if the Activity is missing.
 */
fun Context.shouldShowRationale(permission: String): Boolean =
   (this as? Activity)?.let { activity ->
      androidx.core.app.ActivityCompat
         .shouldShowRequestPermissionRationale(activity, permission)
   } ?: false

/**
 * Opens the system "App info" screen for the current application.
 *
 * Typical usage: when the user has permanently denied a permission
 * and you want to direct them to settings so they can enable it manually.
 */
fun Context.openAppSettings() {
   val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   }
   try {
      startActivity(intent)
   } catch (_: ActivityNotFoundException) {
      // Ignore if settings activity cannot be opened on this device
   }
}
