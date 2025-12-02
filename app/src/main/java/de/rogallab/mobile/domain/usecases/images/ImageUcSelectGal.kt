package de.rogallab.mobile.domain.usecases.images

import android.net.Uri
import androidx.core.net.toUri
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IAppMediaStore
import de.rogallab.mobile.domain.exceptions.IoException

class ImageUcSelectGal(
   private val _mediaStore: IAppMediaStore,
   private val _appStorage: IAppStorage,
) {
   suspend operator fun invoke(uriStringMediaStore: String, groupName:String): Result<Uri> {
      return try {
         if (uriStringMediaStore.isBlank())
            return Result.failure(
               IoException("URI string for MediaStore cannot be empty"))

         val uriMediaStore = uriStringMediaStore.trim().toUri()

         // Validate URI scheme (content:// or file://)
         when (uriMediaStore.scheme) {
            "content", "file" -> {
               // Copy image to app's private storage
               _mediaStore.convertMediaStoreToAppStorage(
                  sourceUri = uriMediaStore,
                  groupName = groupName,
                  appStorage = _appStorage
               )?.let { uriStorage ->
                  return Result.success(uriStorage)
               } ?: run {
                 Result.failure(IoException(
                    "Failed to copy image from gallery to app storage"))
               }
            }
            else -> {
               Result.failure(IoException(
                  "Invalid URI scheme: ${uriMediaStore.scheme}. Expected 'content' or 'file'"))
            }
         }
      } catch (e: Exception) {
         Result.failure(e)
      }
   }
}
