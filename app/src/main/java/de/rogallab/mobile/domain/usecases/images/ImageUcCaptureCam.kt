package de.rogallab.mobile.domain.usecases.images

import android.net.Uri
import androidx.core.net.toUri
import de.rogallab.mobile.Globals.MEDIA_STORE_GROUP_NAME
import de.rogallab.mobile.domain.IMediaStore
import de.rogallab.mobile.domain.exceptions.IoException
import de.rogallab.mobile.domain.utilities.logDebug

class ImageUcCaptureCam(
   private val _mediaStore: IMediaStore
) {
   suspend operator fun invoke(
      capturedImageUriString: String,
      groupName: String
   ): Result<Uri> {
      return try {
         val tag = "<-CaptureImageUC"

         // Parse the image URI captured by the camera
         val sourceUri = capturedImageUriString.toUri()

         val actualGroupName = groupName.ifBlank { MEDIA_STORE_GROUP_NAME }
         val uriMediaStore = _mediaStore.saveImageToMediaStore(groupName,sourceUri )
         uriMediaStore ?: return Result.failure(IoException(
               "Failed to save image to MediaStore"))

         // Convert MediaStore URI to app's private storage
         val uriStorage = _mediaStore.convertMediaStoreToAppStorage(uriMediaStore, groupName)
         logDebug(tag,"uriStorage: $uriStorage")
         uriStorage ?: return Result.failure(IoException(
            "Failed to copy image from MediaStore to app storage"))

         // Return the URI of the image stored in the app's private storage
         Result.success(uriStorage)

      } catch (e: Exception) { Result.failure(e) }
   }
}