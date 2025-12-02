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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.rogallab.mobile.R
import de.rogallab.mobile.domain.utilities.logComp
import de.rogallab.mobile.domain.utilities.logDebug
import de.rogallab.mobile.domain.utilities.logVerbose

@Composable
fun GalSelectImage(
   onSelectImage: (String) -> Unit, // Event ↑
   onError: (String?) -> Unit = {}  // Event ↑
) {
   val tag = "<-GalSelectImage"
   val nComp = remember { mutableIntStateOf(1) }
   SideEffect { logComp(tag, "Composition #${nComp.intValue++}") }

   // callback
   val launcher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.GetContent()
   ) { uri: Uri? ->
      if (uri == null) {
         onError("Auswahl aus Fotogalerie abgebrochen")
      } else  {
         onSelectImage(uri.toString()) // Just pass the URI string to ViewModel
      }
   }

   // UI
   Button(
      onClick = {
         logVerbose(tag, "onclick -> launcher.launch")
         launcher.launch("image/*")
      },
      modifier = Modifier.fillMaxWidth()
   ) {
      Row(
         modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.Start,
         verticalAlignment = Alignment.CenterVertically
      ) {
         val text = stringResource(R.string.selectPhotoFromGallery)
         Icon(
            imageVector = Icons.Default.PhotoLibrary,
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