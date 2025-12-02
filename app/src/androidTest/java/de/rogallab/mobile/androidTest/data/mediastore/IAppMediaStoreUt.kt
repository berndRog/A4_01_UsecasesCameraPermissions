package de.rogallab.mobile.androidTest.data.mediastore

import android.Manifest
import android.R
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import de.rogallab.mobile.Globals
import de.rogallab.mobile.data.local.mediastore.AppMediaStore
import de.rogallab.mobile.domain.IAppMediaStore
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.utilities.newUuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class IAppMediaStoreAndroidTest {

   @get:Rule
   val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
      // Depending on targetSdk some of these might be deprecated,
      // but for instrumentation tests this is usually acceptable:
      Manifest.permission.READ_EXTERNAL_STORAGE,
      Manifest.permission.WRITE_EXTERNAL_STORAGE,
      Manifest.permission.READ_MEDIA_IMAGES
   )

   private lateinit var _context: Context
   private lateinit var _resolver: ContentResolver
   private lateinit var _mediaStore: IAppMediaStore

   @Before
   fun setup() {
      Globals.isInfo = false
      Globals.isDebug = false
      Globals.isVerbose = false

      _context = ApplicationProvider.getApplicationContext()
      _resolver = _context.contentResolver
      _mediaStore = AppMediaStore(_context, Dispatchers.IO)
   }

   //region GroupedImages
   @Test
   fun createGroupedImageUri_createsEntry_ok() = runBlocking {
      // arrange
      val groupName = "TestGroupCreateUri"
      val fileName = "test_image_${System.currentTimeMillis()}"

      // act (suspend call)
      val uri = _mediaStore.createGroupedImageUri(groupName, fileName)

      // assert
      assertNotNull(uri, "Uri must not be null")
      assertEquals("content", uri.scheme)

      // and: is the row actually present in MediaStore?
      _resolver.query(
         uri,
         arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE
         ),
         null,
         null,
         null
      )?.use { cursor ->
         assertTrue(cursor.moveToFirst(), "Inserted row must be readable")
         val displayName =
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
         assertEquals("$fileName.jpg", displayName)
      } ?: fail("Query for created Uri must not be null")
   }
   //endregion

   //region loadBitmap
   @Test
   fun loadBitmap_fromMediaStore_ok() = runBlocking {
      // arrange: write a real test image into MediaStore
      val uri = insertTestBitmapIntoMediaStore(
         groupName = "LoadBitmapGroup",
         width = 10,
         height = 15
      )

      // act
      val bitmap = _mediaStore.loadBitmap(uri)

      // assert
      assertNotNull(bitmap, "Bitmap must not be null")
      assertEquals(10, bitmap.width)
      assertEquals(15, bitmap.height)
      bitmap.recycle()
   }

   @Test
   fun loadBitmap_invalidUri_returnsNull() = runBlocking {
      val invalidUri = Uri.parse("content://de.rogallab.mobile.invalid/does_not_exist")
      val bitmap = _mediaStore.loadBitmap(invalidUri)
      assertNull(bitmap, "Bitmap for invalid uri should be null")
   }
   //endregion

   //region saveImageToMediaStore
   @Test
   fun saveImageToMediaStore_ok() = runBlocking {
      // arrange: source is an actual MediaStore image
      val sourceUri = insertTestBitmapIntoMediaStore(
         groupName = "SourceGroupForSave",
         width = 16,
         height = 16
      )
      val targetGroup = "SavedGroup"

      // act
      val savedUri = _mediaStore.saveImageToMediaStore(targetGroup, sourceUri)

      // assert
      assertNotNull(savedUri, "Saved uri must not be null")
      assertEquals("content", savedUri.scheme)

      // and: saved image is readable
      val savedBitmap = _mediaStore.loadBitmap(savedUri)
      assertNotNull(savedBitmap, "Saved bitmap must be readable")
      savedBitmap.recycle()
   }
   //endregion

   //region deleteImageGroup
   @Test
   fun deleteImageGroup_deletesImages_ok() = runBlocking {
      // arrange – insert several images into the same group
      val groupName = "DeleteGroupTest"
      repeat(3) {
         insertTestBitmapIntoMediaStore(groupName = groupName, width = 8, height = 8)
      }

      val countBefore = countImagesInGroup(groupName)
      assertTrue(
         countBefore >= 3,
         "There should be at least 3 images before deleting (was $countBefore)"
      )

      // act (suspend call)
      val deleted = _mediaStore.deleteImageGroup(groupName)

      // assert
      val countAfter = countImagesInGroup(groupName)
      assertTrue(deleted >= 0, "Deleted count should be >= 0 (was $deleted)")
      assertEquals(0, countAfter, "There should be no images left in the group")
   }
   //endregion

   //region convertDrawableToMediaStore
   @Test
   fun convertDrawableToMediaStore_ok() = runBlocking {
      // arrange
      val groupName = "DrawableGroup"
      val drawableId = R.drawable.ic_menu_camera
      val uuidString = newUuid().toString()

      // act
      val uri = _mediaStore.convertDrawableToMediaStore(
         drawableId = drawableId,
         groupName = groupName,
         uuidString = uuidString
      )

      // assert
      assertNotNull(uri, "Uri must not be null after converting drawable to MediaStore")
      assertEquals("content", uri!!.scheme)

      // and: resulting image is readable
      val bitmap = _mediaStore.loadBitmap(uri)
      assertNotNull(bitmap, "Bitmap from drawable conversion must be readable")
      bitmap.recycle()
   }

   //endregion

   //region convertMediaStoreToAppStorage

   @Test
   fun convertMediaStoreToAppStorage_copiesFile_ok() = runBlocking {
      // arrange: image in MediaStore
      val groupName = "AppStorageGroup"
      val sourceUri = insertTestBitmapIntoMediaStore(
         groupName = "SourceForAppStorage",
         width = 20,
         height = 20
      )

      val appStorage = FakeAppStorage(_context)

      // act
      val appUri = _mediaStore.convertMediaStoreToAppStorage(
         sourceUri = sourceUri,
         groupName = groupName,
         appStorage = appStorage
      )

      // assert
      assertNotNull(appUri, "Uri returned from convertMediaStoreToAppStorage must not be null")
      assertEquals("file", appUri.scheme)

      val destFile = File(appUri.path!!)
      assertTrue(destFile.exists(), "Destination file should exist: ${destFile.absolutePath}")
      assertTrue(destFile.length() > 0, "Destination file should not be empty")
   }

   //endregion

   //region helper functions

   /**
    * Writes a small bitmap into MediaStore (Pictures/<groupName>) and returns its Uri.
    */
   private suspend fun insertTestBitmapIntoMediaStore(
      groupName: String,
      width: Int,
      height: Int
   ): Uri = withContext(Dispatchers.IO) {
      val values = ContentValues().apply {
         put(
            MediaStore.Images.Media.DISPLAY_NAME,
            "test_${groupName}_${System.currentTimeMillis()}.jpg"
         )
         put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
               MediaStore.Images.Media.RELATIVE_PATH,
               "${Environment.DIRECTORY_PICTURES}/$groupName"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
         }
      }

      val uri = _resolver.insert(
         MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
         values
      ) ?: throw IOException("Failed to insert test bitmap into MediaStore")

      // Create a simple test bitmap
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
         val canvas = Canvas(this)
         canvas.drawARGB(255, 255, 0, 0)
      }

      // Write bitmap to MediaStore
      _resolver.openOutputStream(uri)?.use { out ->
         bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
      } ?: throw IOException("Failed to open output stream for test bitmap")

      bitmap.recycle()

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
         val done = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
         }
         _resolver.update(uri, done, null, null)
      }

      return@withContext uri
   }

   private fun countImagesInGroup(groupName: String): Int {
      val selection: String
      val selectionArgs: Array<String>

      selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
         "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
      } else {
         @Suppress("DEPRECATION")
         "${MediaStore.Images.Media.DATA} LIKE ?"
      }
      selectionArgs = arrayOf("%$groupName%")

      return _resolver.query(
         MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
         arrayOf(MediaStore.Images.Media._ID),
         selection,
         selectionArgs,
         null
      )?.use { cursor -> cursor.count } ?: 0
   }

   /**
    * Simple fake implementation of IAppStorage for tests.
    * Copies an image from MediaStore into the private filesDir.
    */
   private class FakeAppStorage(
      private val context: Context
   ) : IAppStorage {

      override suspend fun convertImageUriToAppStorage(
         sourceUri: Uri,
         pathName: String
      ): Uri? = withContext(Dispatchers.IO) {

         val baseDir = File(context.filesDir, pathName)
         if (!baseDir.exists()) {
            baseDir.mkdirs()
         }

         val destFile = File(baseDir, "${UUID.randomUUID()}.jpg")

         context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
               input.copyTo(output)
            }
         } ?: return@withContext null

         return@withContext Uri.fromFile(destFile)
      }

      // --- Dummy implementations not needed for this test suite ---

      override suspend fun convertDrawableToAppStorage(
         drawableId: Int,
         pathName: String,
         uuidString: String?
      ): Uri? = withContext(Dispatchers.IO) {
         null
      }

      override suspend fun loadImage(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
         null
      }

      override suspend fun deleteImage(pathName: String) = withContext(Dispatchers.IO) {
         Unit
      }
   }

   //endregion
}
