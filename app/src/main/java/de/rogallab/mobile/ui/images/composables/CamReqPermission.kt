package de.rogallab.mobile.ui.images.composables

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import de.rogallab.mobile.R
import de.rogallab.mobile.domain.utilities.logVerbose
import de.rogallab.mobile.ui.permissions.RequireCamera

@Composable
fun CamReqPermission(
   handleErrorEvent: (String) -> Unit,
   onPermissionGranted: @Composable () -> Unit
) {
   val tag = "<-CamCheckPermission"
   val nComp = remember { mutableIntStateOf(1) }
   SideEffect { logVerbose(tag, "Composition #${nComp.value++}") }

   val context = LocalContext.current
   var hasCameraPermission by remember {
      mutableStateOf(
         ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
      )
   }

//   val launcher = rememberLauncherForActivityResult(
//      contract = ActivityResultContracts.RequestPermission()
//   ) { isGranted ->
//      logVerbose(tag, "isGranted: $isGranted")
//      hasCameraPermission = isGranted
//      if (!isGranted) handleErrorEvent("Camera permission denied")
//   }

   var ask by remember { mutableStateOf(false) }

   if (hasCameraPermission) {
      onPermissionGranted()
   } else {

      Button(
         onClick = {
            //logVerbose(tag, "onclick -> launcher.launch")
            //launcher.launch(Manifest.permission.CAMERA)
            ask = true
         },
         modifier = Modifier.fillMaxWidth()
      ) {
         Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
         ) {
            val text = stringResource(R.string.permissionCamera)
            Icon(
               //imageVector = Icons.Default.PhotoCamera,
               imageVector = Icons.Default.Settings,
               contentDescription = text
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
               text = text,
               style = MaterialTheme.typography.labelLarge
            )
         }
      } // button
      // When `ask` is true, run the helper to request the permission
      if (ask) {
         RequireCamera(
            onGranted = {
               ask = false
               hasCameraPermission = true
            },
            onDenied = { permanently ->
               ask = false
               handleErrorEvent(
                  if (permanently) "Camera permission permanently denied"
                  else "Camera permission denied"
               )
            }
         )
      }
   }
}