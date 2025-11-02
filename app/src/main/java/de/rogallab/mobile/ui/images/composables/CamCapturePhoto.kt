package de.rogallab.mobile.ui.images.composables

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.core.content.FileProvider
import de.rogallab.mobile.R
import de.rogallab.mobile.domain.utilities.logComp
import de.rogallab.mobile.domain.utilities.logVerbose
import java.io.File

@Composable
fun CamCapturePhoto(
   onCaptureImage: (String) -> Unit,   // Event ↑
   onError: (String?) -> Unit = {}     // Event ↑
) {
   val tag = "<-CamCapturePhoto"
   val nComp = remember { mutableIntStateOf(1) }
   SideEffect { logComp(tag, "Composition #${nComp.value++}") }

   val context = LocalContext.current
   var imageUri by remember { mutableStateOf<Uri?>(null) }

   // Callback
   val launcher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicture()
   ) { success ->
      logVerbose(tag, "success: $success")
      if (success) {
         imageUri?.let { uri ->
            onCaptureImage(uri.toString())
         }
      } else {
         onError("Camera capture failed or was cancelled")
      }
   }

   // UI
   Button(
      onClick = {
         logVerbose(tag, "onclick -> launcher.launch")
         // Create a temporary file for the camera image
         val photoFile = File.createTempFile(
            "photo_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir  // internal temporary storage
         )
         imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            photoFile
         )
         imageUri?.let { uri ->
            launcher.launch(uri)
         }
      },
      modifier = Modifier.fillMaxWidth()
   ) {
      Row(
         modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.Start,
         verticalAlignment = Alignment.CenterVertically
      ) {
         val text = stringResource(R.string.capturePhotoWithCamera)
         Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = text
         )
         Spacer(modifier = Modifier.width(8.dp))
         Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
         )
      }
   }
}