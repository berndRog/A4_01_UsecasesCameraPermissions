package de.rogallab.mobile.domain.usecases.images

import android.net.Uri
import androidx.core.net.toUri
import de.rogallab.mobile.Globals.MEDIA_STORE_GROUP_NAME
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IMediaStore
import de.rogallab.mobile.domain.exceptions.IoException

class ImageUcCaptureCam(
   private val _mediaStore: IMediaStore,
   private val _appStorage: IAppStorage
) {
   suspend operator fun invoke(capturedImageUriString: String, groupName: String): Result<Uri> {
      return try {
         val sourceUri = capturedImageUriString.toUri()
         val groupName = groupName.ifBlank { MEDIA_STORE_GROUP_NAME }

         // Save camera-captured image to MediaStore
         val uriMediaStore = _mediaStore.saveImageToMediaStore(groupName,sourceUri )
         uriMediaStore ?: return Result.failure(IoException(
               "Failed to save image to MediaStore"))

         // Convert image from MediaStore to app's storage
         val uriStorage = _appStorage.convertImageUriToAppStorage(uriMediaStore, groupName)
         uriStorage ?: return Result.failure(IoException(
            "Failed to copy image from MediaStore to app storage"))

         // Return Uri of the image in app's storage
         Result.success(uriStorage)

      } catch (e: Exception) { Result.failure(e) }
   }
}