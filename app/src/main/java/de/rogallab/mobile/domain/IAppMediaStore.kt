package de.rogallab.mobile.domain

import android.graphics.Bitmap
import android.net.Uri

interface IAppMediaStore {
   // Creates a grouped image URI for storing images in a specific group in the MediaStore
   suspend fun createGroupedImageUri(
      groupName: String,
      filename: String? = null
   ): Uri?

   // Saves an image to a specific group in the MediaStore
   suspend fun saveImageToMediaStore(
      groupName: String,
      sourceUri: Uri
   ): Uri?

   // Deletes all images from a specific group
   suspend fun deleteImageGroup(groupName: String): Int

   // Convert a drawable resource to the media store under a specific group
   suspend fun convertDrawableToMediaStore(
      drawableId: Int,
      groupName: String,
      uuidString: String?
   ): Uri?

   // Copies an image from the media store to the app's storage
   suspend fun convertMediaStoreToAppStorage(
      sourceUri: Uri,
      groupName: String,
      appStorage: IAppStorage
   ): Uri?

   suspend fun loadBitmap(
      uri: Uri
   ): Bitmap?

}
