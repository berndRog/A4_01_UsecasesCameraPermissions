package de.rogallab.mobile.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

interface IMediaStore {
   // Creates a session folder for storing images
   fun createSessionFolder(): String

   // Creates a grouped image URI for storing images in a specific group in the MediaStore
   fun createGroupedImageUri(
      groupName: String = createSessionFolder(),
      filename: String? = null
   ): Uri?

   // Saves an image to a specific group in the MediaStore
   suspend fun saveImageToMediaStore(
      groupName: String,
      sourceUri: Uri
   ): Uri?

   // Deletes all images from a specific group
   fun deleteImageGroup(groupName: String): Int

   // Convert a drawable resource to the media store under a specific group
   suspend fun convertDrawableToMediaStore(
      drawableId: Int,
      groupName: String
   ): Uri?

   // Copies an image from the media store to the app's storage
   suspend fun convertMediaStoreToAppStorage(
      sourceUri: Uri,
      groupName: String
   ): Uri?

   suspend fun loadBitmap(
      uri: Uri
   ): Bitmap?

}
