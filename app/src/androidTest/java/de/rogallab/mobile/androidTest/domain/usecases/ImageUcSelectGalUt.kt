package de.rogallab.mobile.androidTest.domain.usecases

import android.net.Uri
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IMediaStore
import de.rogallab.mobile.domain.exceptions.IoException
import de.rogallab.mobile.domain.usecases.images.ImageUcSelectGal
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class ImageUcSelectGalUt {

   private lateinit var fakeMediaStore: FakeMediaStore
   private lateinit var fakeAppStorage: FakeAppStorage
   private lateinit var useCase: ImageUcSelectGal

   @Before
   fun setup() {
      fakeMediaStore = FakeMediaStore()
      fakeAppStorage = FakeAppStorage()
      useCase = ImageUcSelectGal(fakeMediaStore, fakeAppStorage)
   }

   @Test
   fun invoke_blankUri_returnsFailure() = runTest {
      // act
      val result = useCase("", "GroupA")

      // assert
      assertTrue(result.isFailure)
      val ex = result.exceptionOrNull()
      assertIs<IoException>(ex)
      assertTrue(
         ex.message?.contains("cannot be empty") == true,
         "Expected message to mention 'cannot be empty', but was: ${ex.message}"
      )
   }

   @Test
   fun invoke_invalidScheme_returnsFailure() = runTest {
      // arrange
      val uriString = "http://example.com/image.jpg"

      // act
      val result = useCase(uriString, "GroupA")

      // assert
      assertTrue(result.isFailure)
      val ex = result.exceptionOrNull()
      assertIs<IoException>(ex)
      assertTrue(
         ex.message?.contains("Invalid URI scheme") == true,
         "Expected message to mention 'Invalid URI scheme', but was: ${ex.message}"
      )
   }

   @Test
   fun invoke_contentUri_ok_returnsSuccessWithAppStorageUri() = runTest {
      // arrange
      val mediaStoreUriString = "content://media/external/images/media/123"
      val groupName = "GroupB"
      val expectedAppUri = Uri.parse("file:///data/user/0/app/files/images/GroupB/img2.jpg")

      fakeMediaStore.convertResult = expectedAppUri

      // act
      val result = useCase(mediaStoreUriString, groupName)

      // assert
      assertTrue(result.isSuccess)
      assertEquals(expectedAppUri, result.getOrNull())

      assertEquals(
         Uri.parse(mediaStoreUriString),
         fakeMediaStore.lastSourceUri,
         "MediaStore should be called with correct source uri"
      )
      assertEquals(
         groupName,
         fakeMediaStore.lastGroupName,
         "MediaStore should be called with correct groupName"
      )
   }

   @Test
   fun invoke_contentUri_convertReturnsNull_returnsFailure() = runTest {
      // arrange
      val mediaStoreUriString = "content://media/external/images/media/456"
      val groupName = "GroupC"
      fakeMediaStore.convertResult = null  // simuliert Fehler im MediaStore/AppStorage

      // act
      val result = useCase(mediaStoreUriString, groupName)

      // assert
      assertTrue(result.isFailure)
      val ex = result.exceptionOrNull()
      assertIs<IoException>(ex)
      assertTrue(
         ex.message?.contains("Failed to copy image from gallery to app storage") == true,
         "Expected message to mention 'Failed to copy image from gallery to app storage', but was: ${ex.message}"
      )
   }

   // region Fakes

   private class FakeMediaStore : IMediaStore {
      var convertResult: Uri? = null
      var lastSourceUri: Uri? = null
      var lastGroupName: String? = null

      override fun createSessionFolder(): String = "dummy"

      override fun createGroupedImageUri(groupName: String, filename: String?): Uri? = null

      override suspend fun saveImageToMediaStore(groupName: String, sourceUri: Uri): Uri? = null

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
      ): Uri? {
         lastSourceUri = sourceUri
         lastGroupName = groupName
         return convertResult
      }

      override suspend fun loadBitmap(uri: Uri) = null
   }

   private class FakeAppStorage : IAppStorage {
      override suspend fun convertImageUriToAppStorage(
         sourceUri: Uri,
         pathName: String
      ): Uri? = null

      override suspend fun convertDrawableToAppStorage(
         drawableId: Int,
         pathName: String,
         uuidString: String?
      ): Uri? = null

      override suspend fun loadImageFromAppStorage(uri: Uri) = null

      override suspend fun deleteImageOnAppStorage(pathName: String) {}
   }

   // endregion
}
