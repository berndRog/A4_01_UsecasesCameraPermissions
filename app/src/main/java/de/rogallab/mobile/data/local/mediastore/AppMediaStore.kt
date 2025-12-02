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
import de.rogallab.mobile.Globals
import de.rogallab.mobile.domain.IAppMediaStore
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.exceptions.IoException
import de.rogallab.mobile.domain.utilities.logDebug
import de.rogallab.mobile.domain.utilities.newUuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Data-layer implementation of [IAppMediaStore] that encapsulates all interaction
 * with Android's MediaStore API.
 *
 * All heavy I/O work (queries, inserts, deletes, bitmap decoding, file I/O)
 * is executed on a background dispatcher (by default [Dispatchers.IO]).
 */
class AppMediaStore(
   private val _context: Context,
   private val _dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IAppMediaStore {

   /**
    * Create a grouped image URI in MediaStore (e.g. Pictures/<groupName>/<fileName>.jpg).
    *
    * This method:
    *  - Builds appropriate [ContentValues] (DISPLAY_NAME, MIME_TYPE, DATE_TAKEN, RELATIVE_PATH, IS_PENDING)
    *  - Inserts a new row into [MediaStore.Images.Media.EXTERNAL_CONTENT_URI]
    *  - Returns the URI of the new image entry
    *
    * It is `suspend` because inserting into MediaStore is I/O and must not block the main thread.
    */
   override suspend fun createGroupedImageUri(
      groupName: String,
      filename: String?
   ): Uri? = withContext(_dispatcher) {
      try {
         val actualGroupName = groupName.ifBlank { Globals.mediaStoreGroupname }
         val name = filename ?: UUID.randomUUID().toString()
         logDebug(TAG, "createGroupedImageUri: groupName=$actualGroupName, name=$name")

         // Content values for a new image entry in MediaStore
         val imageContentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

            // For Android 10+ use RELATIVE_PATH to define the public folder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
               put(
                  MediaStore.Images.Media.RELATIVE_PATH,
                  "${Environment.DIRECTORY_PICTURES}/$actualGroupName"
               )
               // Mark as pending while we are still writing data
               put(MediaStore.Images.Media.IS_PENDING, 1)
            }
         }

         // Insert the new item into the external Pictures collection
         val uri = _context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageContentValues
         ) ?: throw IoException("Failed to create image URI in MediaStore")

         return@withContext uri
      } catch (e: Exception) {
         if (e is CancellationException) throw e
         throw IoException("Failed to create grouped image URI: ${e.message}")
      }
   }

   /**
    * Delete all images belonging to a specific group (folder).
    *
    * This method:
    *  - For Android 10+ uses RELATIVE_PATH to match the group
    *  - For older devices falls back to deprecated DATA column
    *  - Returns the number of deleted rows
    *
    * It is `suspend` because delete operations on MediaStore are I/O heavy.
    */
   override suspend fun deleteImageGroup(
      groupName: String
   ): Int = withContext(_dispatcher) {
      try {
         val actualGroupName = groupName.ifBlank { Globals.mediaStoreGroupname }

         val (selection, selectionArgs) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
               // Match relative path for grouped images, e.g. Pictures/<groupName>/
               "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%$actualGroupName%")
            } else {
               // Legacy fallback: match by file path (deprecated but required on old APIs)
               "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%$actualGroupName%")
            }

         val deletedCount = _context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            selection,
            selectionArgs
         )

         return@withContext deletedCount
      } catch (e: Exception) {
         if (e is CancellationException) throw e

         val actualGroupName = groupName.ifBlank { Globals.mediaStoreGroupname }
         throw IoException("Failed to delete image group: $actualGroupName: ${e.message}")
      }
   }

   /**
    * Check whether there is at least one image in a given MediaStore group (folder).
    *
    * Returns:
    *  - true  → at least one image exists in that group
    *  - false → group does not exist or an error occurred
    *
    * This is not part of the IMediaStore interface in your snippet, but can be useful as helper.
    */
   suspend fun doesImageGroupExist(
      groupName: String
   ): Boolean = withContext(_dispatcher) {
      try {
         val actualGroupName = groupName.ifBlank { Globals.mediaStoreGroupname }
         val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
         val projection = arrayOf(MediaStore.Images.Media._ID)

         val (selection, selectionArgs) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
               "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%$actualGroupName%")
            } else {
               "${MediaStore.Images.Media.DATA} LIKE ?" to arrayOf("%$actualGroupName%")
            }

         _context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            ?.use { cursor ->
               // If cursor can move to first row, there is at least one entry
               return@withContext cursor.moveToFirst()
            }

         // No cursor or no rows
         return@withContext false
      } catch (e: Exception) {
         if (e is CancellationException) throw e
         // In case of errors we return false instead of throwing,
         // because existence check is usually non-critical.
         return@withContext false
      }
   }

   /**
    * Save an image (referenced by [sourceUri]) into MediaStore under a specific group.
    *
    * Extended behavior (as requested):
    *  1. Check if the image group already exists in MediaStore.
    *  2. If it does NOT exist, we "create" it implicitly by creating the first image URI
    *     via [createGroupedImageUri]. MediaStore will create the folder structure
    *     automatically when inserting the image entry.
    *  3. In both cases, we obtain a URI (via [createGroupedImageUri]) and then
    *     compress and write the bitmap data to that URI.
    *
    * Returns:
    *  - URI of the saved image, or
    *  - null on failure
    */
   override suspend fun saveImageToMediaStore(
      groupName: String,
      sourceUri: Uri
   ): Uri? = withContext(_dispatcher) {

      var bitmap: Bitmap? = null
      var uriMediaStore: Uri? = null

      try {
         val actualGroupName = groupName.ifBlank { Globals.mediaStoreGroupname }
         logDebug(TAG, "saveImageToMediaStore: groupName=$actualGroupName, sourceUri=$sourceUri")

         // Load bitmap from the given sourceUri (any content/file URI)
         bitmap = loadBitmap(sourceUri) ?: return@withContext null

         // Generate a unique file name (without extension, extension is added in createGroupedImageUri)
         val fileNameWithoutExtension = newUuid().toString()

         // 1) Check if the group already exists in MediaStore
         val groupExists = doesImageGroupExist(actualGroupName)
         if (!groupExists) {
            logDebug(TAG, "Image group does not exist, will be created now: $actualGroupName")
         } else {
            logDebug(TAG, "Image group already exists: $actualGroupName")
         }

         // 2) Create a new MediaStore entry in the target group.
         //    This will also implicitly ensure the group/folder exists.
         uriMediaStore = createGroupedImageUri(
            groupName = actualGroupName,
            filename = fileNameWithoutExtension
         )

         if (uriMediaStore == null) {
            logDebug(TAG, "Failed to create image URI in MediaStore for group: $actualGroupName")
            return@withContext null
         }

         // 3) Write the bitmap contents into the created MediaStore item
         _context.contentResolver.openOutputStream(uriMediaStore)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
               throw IllegalStateException("Failed to compress bitmap")
            }
         } ?: throw IllegalStateException("Cannot open output stream for URI: $uriMediaStore")

         // 4) Make the file visible (not pending anymore) for Android 10+
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply {
               put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            _context.contentResolver.update(uriMediaStore, done, null, null)
         }

         return@withContext uriMediaStore
      } catch (e: Exception) {
         if (e is CancellationException) throw e

         // If something goes wrong, try to clean up the partially created entry
         uriMediaStore?.let {
            _context.contentResolver.delete(it, null, null)
         }
         e.printStackTrace()
         return@withContext null
      } finally {
         // Free bitmap memory explicitly
         bitmap?.recycle()
      }
   }

   /**
    * Copy a drawable resource into MediaStore and return its URI.
    *
    * Typical use-case:
    *  - Seed/demo images
    *  - Shipping predefined avatar/background images
    *
    * Steps:
    *  1. Create a MediaStore URI (group + optional UUID file name)
    *  2. Load the drawable and draw it to a [Bitmap]
    *  3. Compress and write bitmap to the MediaStore URI
    *
    * Returns:
    *  - URI of the saved image, or
    *  - null if the URI could not be created
    */
   override suspend fun convertDrawableToMediaStore(
      drawableId: Int,
      groupName: String,
      uuidString: String?
   ): Uri? = withContext(_dispatcher) {

      var bitmap: Bitmap? = null
      var imageUri: Uri? = null

      try {
         // Create a new MediaStore entry for the drawable
         imageUri = createGroupedImageUri(groupName, uuidString) ?: return@withContext null

         // Load the drawable resource
         val drawable = _context.getDrawable(drawableId)
            ?: throw IllegalArgumentException("Drawable not found: $drawableId")

         // Create bitmap with intrinsic size (fallback to at least 1x1)
         val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
         val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
         bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

         // Draw the drawable into the bitmap
         Canvas(bitmap).also { canvas ->
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
         }

         // Save bitmap as JPEG into MediaStore: /storage/emulated/0/Pictures/<groupName>/<fileName>.jpg
         _context.contentResolver.openOutputStream(imageUri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) {
               throw IllegalStateException("Failed to compress bitmap")
            }
         } ?: throw IllegalStateException("Cannot open output stream for URI: $imageUri")

         // Mark as not pending anymore (Android 10+)
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply {
               put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            _context.contentResolver.update(imageUri, done, null, null)
         }

         return@withContext imageUri
      } catch (e: Exception) {
         if (e is CancellationException) throw e

         // Clean up partially created entry if needed
         imageUri?.let {
            _context.contentResolver.delete(it, null, null)
         }
         throw IoException("Failed to convert drawable to MediaStore: ${e.message}")
      } finally {
         // Always recycle bitmap to avoid memory leaks
         bitmap?.recycle()
      }
   }

   /**
    * Copy an image from MediaStore (or any content URI) into the app's private storage.
    *
    * This delegates the actual file I/O to [IAppStorage], but enforces execution
    * on the I/O dispatcher and wraps potential failures in a meaningful exception.
    */
   override suspend fun convertMediaStoreToAppStorage(
      sourceUri: Uri,
      groupName: String,
      appStorage: IAppStorage
   ): Uri? = withContext(_dispatcher) {
      try {
         return@withContext appStorage.convertImageUriToAppStorage(
            sourceUri = sourceUri,
            pathName = "images/$groupName"
         )
      } catch (e: Exception) {
         if (e is CancellationException) throw e

         throw IllegalStateException(
            "Failed to copy image from MediaStore to app storage",
            e
         )
      }
   }

   /**
    * Load a [Bitmap] from any URI (MediaStore URI, file URI, content URI).
    *
    * For Android 9+ it uses [ImageDecoder] (modern API).
    * For older devices it falls back to the deprecated
    * [MediaStore.Images.Media.getBitmap] method.
    *
    * Returns:
    *  - Decoded [Bitmap], or
    *  - null on failure
    */
   override suspend fun loadBitmap(
      uri: Uri
   ): Bitmap? = withContext(_dispatcher) {
      try {
         return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(_context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
         } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(_context.contentResolver, uri)
         }
      } catch (e: Exception) {
         if (e is CancellationException) throw e

         e.printStackTrace()
         return@withContext null
      }
   }

   companion object {
      private const val TAG = "<-AppMediaStore"
   }
}
