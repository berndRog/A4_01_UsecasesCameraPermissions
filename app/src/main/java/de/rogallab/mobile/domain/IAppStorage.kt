package de.rogallab.mobile.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

interface IAppStorage {

   // Converts a drawable resource to an image file  the app's storage
   suspend fun convertDrawableToAppStorage(
      drawableId: Int,
      pathName: String,  // images/people31
      uuidString: String?
   ): Uri?


   // Copies an image from the media store to the app's storage
   suspend fun convertImageUriToAppStorage(
      sourceUri: Uri,
      pathName: String
   ): Uri?


   suspend fun loadImageFromAppStorage(uri: Uri): Bitmap?

   suspend fun deleteImageOnAppStorage(pathName:String): Unit

}
