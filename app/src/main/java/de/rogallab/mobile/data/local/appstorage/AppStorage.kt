package de.rogallab.mobile.data.local.appstorage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.exceptions.IoException
import de.rogallab.mobile.domain.utilities.logError
import de.rogallab.mobile.domain.utilities.newUuid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AppStorage(
   private val _context: Context,
   private val _dispatcher: CoroutineDispatcher
) : IAppStorage {

   // Convert a drawable resource to an image file in app's private storage
   override suspend fun convertDrawableToAppStorage(
      drawableId: Int,
      pathName: String,
      uuidString: String?
   ): Uri? = withContext(Dispatchers.IO) {

      var bitmap: Bitmap? = null

      try {
         var uuidLocal = if(uuidString.isNullOrBlank()) newUuid() else uuidString

         // Load bitmap from drawable resource
         bitmap = BitmapFactory.decodeResource(_context.resources, drawableId)
            ?: throw IllegalArgumentException("Failed to decode drawable resource: $drawableId")

         // Prepare destination directory and file
         val imagesDir = File(_context.filesDir, pathName).apply { if (!exists()) mkdirs() }
         val imageFile = File(imagesDir, "$uuidLocal.jpg")

         // Save bitmap to app's private files directory
         FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
         }
         return@withContext Uri.fromFile(imageFile)
      } catch (e: Exception) {
         throw IoException("Failed to convert drawable to app storage: ${e.message}")
      } finally {
         bitmap?.recycle()
      }
   }


   // Copy image from Image Uri to app's private storage
   override suspend fun convertImageUriToAppStorage(
      sourceUri: Uri,
      pathName: String   // images/people41
   ): Uri? = withContext(_dispatcher) {
      try {
         // Create destination file with unique name
         val dir = File(_context.filesDir, pathName).apply { if (!exists()) mkdirs() }
         val destinationFile = File(dir, "IMG_${System.currentTimeMillis()}.jpg")

         // Copy from Image URI to app storage
         _context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
               inputStream.copyTo(outputStream)
            }
         } ?: throw IllegalStateException("Cannot open source URI: $sourceUri")

         // Return file URI
         return@withContext Uri.fromFile(destinationFile)
      } catch (e: Exception) {
         throw IllegalStateException("Failed to copy from image Uri to app storage", e)
      }
   }
   
   
   // Load bitmap from any URI (MediaStore, file URI, content URI)
   override suspend fun loadImageFromAppStorage(uri: Uri): Bitmap? = withContext(_dispatcher) {
      try {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(_context.contentResolver, uri) // Fix: _context
            return@withContext ImageDecoder.decodeBitmap(source)
         } else {
            @Suppress("DEPRECATION")
            return@withContext MediaStore.Images.Media.getBitmap(_context.contentResolver, uri) // Fix: _context
         }
      } catch (e: Exception) {
         logError("<-deleteImageOnAppStorage",
            e.localizedMessage ?: "Failed to load image from app storage")
         return@withContext null
      }
   }

   override suspend fun deleteImageOnAppStorage(
      pathName:String
   ): Unit = withContext(_dispatcher) {
      try {
         // pathName for the image file
         File(pathName).apply {
            this.absoluteFile.delete()
         }
      } catch(e:IOException ) {
         logError("<-deleteImageOnAppStorage",
            e.localizedMessage ?: "Failed to delete file: $pathName")
         throw e
      }
   }



   companion object {
      private const val TAG = "<-MediaStore"
   }
}