package de.rogallab.mobile.ui.images.composables
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.rogallab.mobile.domain.utilities.logComp

@Composable
fun SelectAndShowImage(
   localImage: String?,                 // State ↓
// remoteImage: String?,                // State ↓
// imageLoader: ImageLoader,
   handleError: (String?) -> Unit,      // Event ↑
   onSelectImage: (String) -> Unit,     // Event ↑ select from gallery
   onCaptureImage: (String) -> Unit,    // Event ↑ capture with camera
) {
   val tag = "<-SelectAndShowImage"
   val nComp = remember { mutableIntStateOf(1) }
   SideEffect { logComp(tag, "Composition #${nComp.value++}") }

   Row(modifier = Modifier
      .padding(vertical = 8.dp)
      .fillMaxWidth()) {
      val imagePath: String? = localImage  // whichImagePath(localImage, remoteImage)
      if(!imagePath.isNullOrEmpty()) {
         AsyncImage(
            modifier = Modifier
               .size(width = 150.dp, height = 200.dp)
               .clip(RoundedCornerShape(percent = 5)),
            model = imagePath,
            //imageLoader = imageLoader,
            contentDescription = "Bild des Kontakts",
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop
         )
      }
      Column(
         modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
         verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
         GalSelectImage(
            onSelectImage = onSelectImage
         )

         CamCheckPermission(
            handleErrorEvent = handleError,
            onPermissionGranted = {
               CamCapturePhoto(
                  onCaptureImage = onCaptureImage,
                  onErrorEvent = handleError
               )
            }
         )
      }
   }
}