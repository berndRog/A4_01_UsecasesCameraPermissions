package de.rogallab.mobile.androidTest.domain.usecases

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import de.rogallab.mobile.Globals
import de.rogallab.mobile.domain.IAppMediaStore
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.exceptions.IoException
import de.rogallab.mobile.domain.usecases.images.ImageUcCaptureCam
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImageUcCaptureCamUt {

   private lateinit var fakeMediaStore: FakeMediaStore
   private lateinit var fakeAppStorage: FakeAppStorage
   private lateinit var useCase: ImageUcCaptureCam

   @Before
   fun setup() {
      // Default group name used when an empty groupName is passed in
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

      // Ensure that the MediaStore fake received the correct parameters
      assertEquals(capturedUriString.toUri(), fakeMediaStore.lastSaveSourceUri)
      assertEquals(groupName, fakeMediaStore.lastSaveGroupName)

      // Ensure that the AppStorage fake received the correct parameters
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
      assertTrue(
         ex?.message?.contains("Failed to save image to MediaStore") == true,
         "Expected error message to mention failure saving to MediaStore"
      )
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
      assertTrue(
         ex?.message?.contains("Failed to copy image from MediaStore to app storage") == true,
         "Expected error message to mention failure copying to app storage"
      )
   }

   // --- Fakes ---------------------------------------------------------------

   /**
    * Fake implementation of [IAppMediaStore] for unit-testing the use case.
    *
    * Only [saveImageToMediaStore] is actually used in the tests and contains
    * logic to record the parameters. All other methods are simple stubs.
    */
   private class FakeMediaStore : IAppMediaStore {
      var saveImageToMediaStoreResult: Uri? = null
      var lastSaveGroupName: String? = null
      var lastSaveSourceUri: Uri? = null

      override suspend fun createGroupedImageUri(
         groupName: String,
         filename: String?
      ): Uri? = null

      override suspend fun saveImageToMediaStore(
         groupName: String,
         sourceUri: Uri
      ): Uri? {
         lastSaveGroupName = groupName
         lastSaveSourceUri = sourceUri
         return saveImageToMediaStoreResult
      }

      override suspend fun deleteImageGroup(groupName: String): Int = 0

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

      override suspend fun loadBitmap(uri: Uri): Bitmap? = null
   }

   /**
    * Fake implementation of [IAppStorage] used by the use case tests.
    *
    * Only [convertImageUriToAppStorage] is relevant for the current tests.
    * It records parameters and returns a pre-configured Uri.
    */
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

      // Dummy implementations for the remaining interface methods
      override suspend fun convertDrawableToAppStorage(
         drawableId: Int,
         pathName: String,
         uuidString: String?
      ): Uri? = null

      override suspend fun loadImage(uri: Uri): Bitmap? = null

      override suspend fun deleteImage(pathName: String) {}
   }
}
