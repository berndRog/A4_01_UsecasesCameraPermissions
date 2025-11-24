package de.rogallab.mobile.androidTest.domain.usecases

import android.net.Uri
import androidx.core.net.toUri
import de.rogallab.mobile.Globals
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IMediaStore
import de.rogallab.mobile.domain.exceptions.IoException
import de.rogallab.mobile.domain.usecases.images.ImageUcCaptureCam
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class ImageUcCaptureCamUt {

   private lateinit var fakeMediaStore: FakeMediaStore
   private lateinit var fakeAppStorage: FakeAppStorage
   private lateinit var useCase: ImageUcCaptureCam

   @Before
   fun setup() {
      Globals.mediaStoreGroupname = "DefaultGroup"

      fakeMediaStore = FakeMediaStore()
      fakeAppStorage = FakeAppStorage()
      useCase = ImageUcCaptureCam(fakeMediaStore, fakeAppStorage)
   }

   @Test
   fun invoke_ok_returnsSuccessWithAppStorageUri() = runTest {
      // arrange
      val capturedUriString = "content://camera/image1"
      val groupName = "MyGroup"
      val uriMediaStore = "content://mediastore/image1".toUri()
      val uriAppStorage = "file:///data/user/0/app/files/images/MyGroup/img1.jpg".toUri()

      fakeMediaStore.saveImageToMediaStoreResult = uriMediaStore
      fakeAppStorage.convertResult = uriAppStorage

      // act
      val result = useCase(capturedUriString, groupName)

      // assert
      assertTrue(result.isSuccess)
      assertEquals(uriAppStorage, result.getOrNull())
      assertEquals(capturedUriString.toUri(), fakeMediaStore.lastSaveSourceUri)
      assertEquals(groupName, fakeMediaStore.lastSaveGroupName)
      assertEquals(uriMediaStore, fakeAppStorage.lastSourceUri)
      assertEquals("images/$groupName", fakeAppStorage.lastPathName)
   }

   @Test
   fun invoke_mediaStoreReturnsNull_returnsFailure() = runTest {
      // arrange
      val capturedUriString = "content://camera/image2"

      fakeMediaStore.saveImageToMediaStoreResult = null

      // act
      val result = useCase(capturedUriString, "")

      // assert
      assertTrue(result.isFailure)
      val ex = result.exceptionOrNull()
      assertIs<IoException>(ex)
      assertTrue(ex.message?.contains("Failed to save image to MediaStore") == true)
   }

   @Test
   fun invoke_appStorageReturnsNull_returnsFailure() = runTest {
      // arrange
      val capturedUriString = "content://camera/image3"
      val uriMediaStore = "content://mediastore/image3".toUri()

      fakeMediaStore.saveImageToMediaStoreResult = uriMediaStore
      fakeAppStorage.convertResult = null

      // act
      val result = useCase(capturedUriString, "GroupX")

      // assert
      assertTrue(result.isFailure)
      val ex = result.exceptionOrNull()
      assertIs<IoException>(ex)
      assertTrue(ex.message?.contains("Failed to copy image from MediaStore to app storage") == true)
   }

   // --- Fakes ---

   private class FakeMediaStore : IMediaStore {
      var saveImageToMediaStoreResult: Uri? = null
      var lastSaveGroupName: String? = null
      var lastSaveSourceUri: Uri? = null

      override fun createSessionFolder(): String = "dummy"

      override fun createGroupedImageUri(groupName: String, filename: String?): Uri? = null

      override suspend fun saveImageToMediaStore(groupName: String, sourceUri: Uri): Uri? {
         lastSaveGroupName = groupName
         lastSaveSourceUri = sourceUri
         return saveImageToMediaStoreResult
      }

      override fun deleteImageGroup(groupName: String): Int = 0

      override suspend fun convertDrawableToMediaStore(
         drawableId: Int,
         groupName: String,
         uuidString: String?
      ): Uri? = null

      override suspend fun convertMediaStoreToAppStorage(
         sourceUri: Uri,
         groupName: String,
         appStorage: IAppStorage
      ): Uri? = null

      override suspend fun loadBitmap(uri: Uri) = null
   }

   private class FakeAppStorage : IAppStorage {
      var convertResult: Uri? = null
      var lastSourceUri: Uri? = null
      var lastPathName: String? = null

      override suspend fun convertImageUriToAppStorage(
         sourceUri: Uri,
         pathName: String
      ): Uri? {
         lastSourceUri = sourceUri
         lastPathName = pathName
         return convertResult
      }

      // Dummy-Implementierungen für restliche Interface-Methoden
      override suspend fun convertDrawableToAppStorage(
         drawableId: Int,
         pathName: String,
         uuidString: String?
      ): Uri? = null

      override suspend fun loadImageFromAppStorage(uri: Uri) = null

      override suspend fun deleteImageOnAppStorage(pathName: String) {}
   }
}
