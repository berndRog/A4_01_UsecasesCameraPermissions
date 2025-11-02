package de.rogallab.mobile.ui.images.composables

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.rogallab.mobile.R
import de.rogallab.mobile.domain.utilities.logVerbose
import de.rogallab.mobile.ui.permissions.RequireCamera
import de.rogallab.mobile.ui.permissions.RequireLocationWhileInUse

/**
 * Requests CAMERA + while-in-use LOCATION and fetches a single GPS fix for geotagging.
 *
 * - Shows a single CTA button (like your CamCheckPermission).
 * - On success: calls [onLocationReady] with (lat, lon) and renders [onPermissionGranted] content (e.g. Camera UI).
 */
@Composable
fun CamGeoCheckPermission(
    handleErrorEvent: (String) -> Unit,
    onLocationReady: (lat: Double, lon: Double) -> Unit,
    onPermissionGranted: @Composable () -> Unit
) {
    val tag = "<-CamGeoCheckPermission>"
    val nComp = remember { mutableIntStateOf(1) }
    SideEffect { logVerbose(tag, "Composition #${nComp.value++}") }

    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    // Stage toggles to trigger JIT requests
    var askCamera by remember { mutableStateOf(false) }
    var askLocation by remember { mutableStateOf(false) }

    if (hasCamera && hasLocation) {
        onPermissionGranted()
    } else {
        // Single CTA: requests what is missing (camera, then location)
        Button(
            onClick = {
                when {
                    !hasCamera -> askCamera = true
                    !hasLocation -> askLocation = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val text = if (!hasCamera) {
                    stringResource(R.string.permissionCamera)
                } else {
                    "permission location string" //stringResource(R.string.permissionLocation) // add to strings if not present
                }
                Icon(
                    imageVector = if (!hasCamera) Icons.Default.PhotoCamera else Icons.Default.Settings,
                    contentDescription = text
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // 1) Ask for CAMERA first (if missing)
        if (askCamera) {
            RequireCamera(
                onGranted = {
                    logVerbose(tag, "CAMERA granted")
                    askCamera = false
                    hasCamera = true
                    // Immediately continue with location if still missing
                    if (!hasLocation) askLocation = true
                },
                onDenied = { permanently ->
                    askCamera = false
                    handleErrorEvent(
                        if (permanently) "Camera permission permanently denied"
                        else "Camera permission denied"
                    )
                }
            )
        }

        // 2) Then ask for while-in-use LOCATION and fetch a single fix
        if (askLocation) {
            RequireLocationWhileInUse(
                onGranted = {
                    logVerbose(tag, "LOCATION granted")
                    askLocation = false
                    hasLocation = true
                    // Get one GPS fix for geotagging
                    requestSingleFix(context) { lat, lon ->
                        onLocationReady(lat, lon)
                    }
                },
                onDenied = { permanently ->
                    askLocation = false
                    handleErrorEvent(
                        if (permanently) "Location permission permanently denied"
                        else "Location permission denied"
                    )
                }
            )
        }
    }
}

@SuppressLint("MissingPermission") // guarded by RequireLocationWhileInUse
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
