package de.rogallab.mobile.data.local.mediastore

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import de.rogallab.mobile.Globals.MEDIA_STORE_GROUP_NAME
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IMediaStore
import de.rogallab.mobile.domain.exceptions.IoException
import de.rogallab.mobile.domain.utilities.logDebug
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.text.compareTo

class MediaStore(
   private val _context: Context,
   private val _ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IMediaStore {

   // Create a session folder name based on current date and time
   override fun createSessionFolder(): String {
      val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
      val timeFormat = SimpleDateFormat("HHmm", Locale.US)
      val date = Date()
      return "A4_01_${dateFormat.format(date)}_${timeFormat.format(date)}"
   }

   // Create a grouped image URI for storing images in a specific group
   override fun createGroupedImageUri(
      groupName: String,
      filename: String?
   ): Uri? {
      return try {
         val actualGroupName = groupName.ifBlank { MEDIA_STORE_GROUP_NAME }
         val name = filename ?: UUID.randomUUID().toString()
         logDebug(TAG, "createGroupedImageUri: groupName=$actualGroupName, name=$name")

         // Create a new image entry in MediaStore
         // Use MediaStore.Images.Media.EXTERNAL_CONTENT_URI for external storage
         val imageContentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

            // Group images in custom subfolder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
               put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$actualGroupName")
            }
         }
         // For Android 10 and above, useRELATIVE_PATH to specify the folder
         _context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageContentValues
         )?: throw IoException("Failed to create image URI in MediaStore")
      } catch (e: Exception) {
         throw IoException("Failed to create grouped image URI ${e.message}")
      }
   }


   // Delete all images from a specific folder/group
   override fun deleteImageGroup(
      groupName: String
   ): Int =
      try {
         val actualGroupName = groupName.ifBlank { MEDIA_STORE_GROUP_NAME }

         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%$actualGroupName%")

            _context.contentResolver.delete(
               MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
               selection,
               selectionArgs
            )
         } else {
            // For older Android versions, delete by DATA column (deprecated but needed)
            val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("%$actualGroupName%")

            // This will delete all images that match the group name in the file path
            _context.contentResolver.delete(
               MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
               selection,
               selectionArgs
            )
         }
      } catch (e: Exception) {
         val actualGroupName = groupName.ifBlank { MEDIA_STORE_GROUP_NAME }
         throw IoException("Failed to delete image group: $actualGroupName: ${e.message}")
      }

   // Save an image to MediaStore under a specific group
   // Returns the URI of the saved image or null if failed
   override suspend fun saveImageToMediaStore(
      groupName: String,
      sourceUri: Uri
   ): Uri? = withContext(_ioDispatcher) { // heavy io operation

      var bitmap: Bitmap? = null
      var uriMediaStore: Uri? = null

      try {
         val actualGroupName = groupName.ifBlank { MEDIA_STORE_GROUP_NAME }
         logDebug(TAG, "saveImageToMediaStore: groupName=$actualGroupName, sourceUri=$sourceUri")

         // Load bitmap from source URI
         bitmap = loadBitmap(sourceUri) ?: return@withContext null

         // Prepare content values for new image
         val fileName = "${UUID.randomUUID()}.jpg"
         val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
               put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$actualGroupName")
               put(MediaStore.Images.Media.IS_PENDING, 1)
            }
         }

         // Insert new image into MediaStore
         uriMediaStore = _context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
         ) ?: return@withContext null

         // Write bitmap data to MediaStore
         _context.contentResolver.openOutputStream(uriMediaStore)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
               throw IllegalStateException("Failed to compress bitmap")
            }
         } ?: throw IllegalStateException("Cannot open output stream for URI: $uriMediaStore")

         // Mark image as not pending (available) for Android Q and above
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            _context.contentResolver.update(uriMediaStore, done, null, null)
         }

         return@withContext uriMediaStore
      } catch (e: Exception) {
         uriMediaStore?.let { _context.contentResolver.delete(it, null, null) }
         e.printStackTrace()
         return@withContext null
      } finally {
         bitmap?.recycle()
      }
   }

   // copy a drawable resource to MediaStore and return its URI
   // External Pictures: /storage/emulated/0/Pictures/[your_app_folder]/
   override suspend fun convertDrawableToMediaStore(
      drawableId: Int,
      groupName: String,
      uuidString: String?
   ): Uri? = withContext(_ioDispatcher) {

      var bitmap: Bitmap? = null

      try {
         // Create URI for new image in MediaStore
         val imageUri = createGroupedImageUri(groupName, uuidString) ?: return@withContext null

         // Get drawable resource
         val drawable = _context.getDrawable(drawableId)
            ?: throw IllegalArgumentException("Drawable not found: $drawableId")

         // Convert drawable to bitmap
         val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
         val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
         bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
         Canvas(bitmap).also { canvas ->
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
         }

         // Save bitmap to MediaStore  /storage/emulated/0/Pictures/<groupName>/<fileName>.jpg
         _context.contentResolver.openOutputStream(imageUri)?.use { out ->
            if(!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
               throw IllegalStateException("Failed to compress bitmap")
            }
         } ?: throw IllegalStateException("Cannot open output stream for URI: $imageUri")

         return@withContext imageUri

      } catch (e: Exception) {
         throw IoException("Failed to convert drawable to MediaStore: ${e.message}")
      } finally {
         bitmap?.recycle()
      }
   }

   // Copy image from MediaStore to app's private storage
   override suspend fun convertMediaStoreToAppStorage(
      sourceUri: Uri,
      groupName: String,
      appStorage: IAppStorage
   ): Uri? = withContext(_ioDispatcher) {
      try {
         return@withContext appStorage.convertImageUriToAppStorage(
            sourceUri = sourceUri,
            pathName = "images/$groupName"
         )
      } catch (e: Exception) {
         throw IllegalStateException("Failed to copy image from MediaStore to app storage", e)
      }
   }

   // Load bitmap from any URI (MediaStore, file URI, content URI)
   override suspend fun loadBitmap(
      uri: Uri
   ): Bitmap? = withContext(_ioDispatcher) {
      try {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(_context.contentResolver, uri) // Fix: _context
            return@withContext ImageDecoder.decodeBitmap(source)
         } else {
            @Suppress("DEPRECATION")
            return@withContext MediaStore.Images.Media.getBitmap(_context.contentResolver, uri) // Fix: _context
         }
      } catch (e: Exception) {
         e.printStackTrace()
         return@withContext null
      }
   }

   companion object {
      private const val TAG = "<-MediaStore"
   }
}