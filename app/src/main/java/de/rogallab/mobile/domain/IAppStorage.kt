package de.rogallab.mobile.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

interface IAppStorage {

   // Converts a drawable resource to an image file  the app's storage
   suspend fun convertDrawableToAppStorage(
      context: Context,
      drawableId: Int,
      pathName: String,  // images/people31
      uuidString: String?
   ): Uri?

   suspend fun loadImageFromAppStorage(uri: Uri): Bitmap?

   suspend fun deleteImageOnAppStorage(pathName:String): Unit

}
