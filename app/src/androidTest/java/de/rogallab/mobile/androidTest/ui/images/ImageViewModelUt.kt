package de.rogallab.mobile.androidTest.ui.images

import android.net.Uri
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import de.rogallab.mobile.androidTest.MainDispatcherRule
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IMediaStore
import de.rogallab.mobile.domain.IImageUseCases
import de.rogallab.mobile.domain.usecases.images.ImageUcCaptureCam
import de.rogallab.mobile.domain.usecases.images.ImageUcSelectGal
import de.rogallab.mobile.ui.images.ImageViewModel
import de.rogallab.mobile.ui.navigation.INavHandler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ImageViewModelUt {

   @get:Rule
   val mainRule = MainDispatcherRule()

   private lateinit var fakeImageUseCases: FakeImageUseCases
   private lateinit var fakeNavHandler: FakeNavHandler
   private lateinit var viewModel: ImageViewModel

   @Before
   fun setup() {
      fakeImageUseCases = FakeImageUseCases()
      fakeNavHandler = FakeNavHandler()
      viewModel = ImageViewModel(fakeImageUseCases, fakeNavHandler)
   }

   //region selectImage
   @Test
   fun selectImage_success_callsOnResultWithUriString() = runTest {
      // arrange
      val inputUriString = "content://gallery/image1"
      val groupName = "GroupSel"
      val resultUri = "file:///app/images/GroupSel/img1.jpg".toUri()

      fakeImageUseCases.nextSelectResult = Result.success(resultUri)

      var callbackResult: String? = null

      // act
      viewModel.selectImage(inputUriString, groupName) { result ->
         callbackResult = result
      }
      advanceUntilIdle()  // wartet bis viewModelScope-Job fertig ist

      // assert
      assertTrue(fakeImageUseCases.selectCalled)
      assertEquals(resultUri.toString(), callbackResult)
   }

   @Test
   fun selectImage_failure_callsOnResultWithNull() = runTest {
      // arrange
      val inputUriString = "content://gallery/image2"
      val groupName = "GroupSelFail"

      fakeImageUseCases.nextSelectResult =
         Result.failure(RuntimeException("select error"))

      var callbackResult: String? = "initial"

      // act
      viewModel.selectImage(inputUriString, groupName) { result ->
         callbackResult = result
      }
      advanceUntilIdle()

      // assert
      assertTrue(fakeImageUseCases.selectCalled)
      assertNull(callbackResult)
   }
   //endregion

   //region captureImage
   @Test
   fun captureImage_success_callsOnResultWithUriString() = runTest {
      // arrange
      val inputUriString = "content://camera/image1"
      val groupName = "GroupCap"
      val resultUri = "file:///app/images/GroupCap/cam1.jpg".toUri()

      fakeImageUseCases.nextCaptureResult = Result.success(resultUri)

      var callbackResult: String? = null

      // act
      viewModel.captureImage(inputUriString, groupName) { result ->
         callbackResult = result
      }
      advanceUntilIdle()

      // assert
      assertTrue(fakeImageUseCases.captureCalled)
      assertEquals(resultUri.toString(), callbackResult)
   }

   @Test
   fun captureImage_failure_callsOnResultWithNull() = runTest {
      // arrange
      val inputUriString = "content://camera/image2"
      val groupName = "GroupCapFail"

      fakeImageUseCases.nextCaptureResult =
         Result.failure(RuntimeException("capture error"))

      var callbackResult: String? = "initial"

      // act
      viewModel.captureImage(inputUriString, groupName) { result ->
         callbackResult = result
      }
      advanceUntilIdle()

      // assert
      assertTrue(fakeImageUseCases.captureCalled)
      assertNull(callbackResult)
   }
   //endregion

   //region Fakes
   /**
    * Fake-Implementierung von IImageUseCases, die intern echte ImageUcSelectGal / ImageUcCaptureCam
    * verwendet, aber deren Verhalten über nextSelectResult / nextCaptureResult steuert.
    *
    * So testen wir im ViewModel nur:
    *  - dass fold(onSuccess/onFailure) korrekt behandelt wird
    *  - und dass onResult richtig gesetzt wird
    */
   private class FakeImageUseCases : IImageUseCases {

      // diese Result-Werte steuern wir im Test
      var nextSelectResult: Result<Uri> = Result.success("file:///dummy/select-ok".toUri())
      var nextCaptureResult: Result<Uri> = Result.success("file:///dummy/capture-ok".toUri())

      var selectCalled: Boolean = false
      var captureCalled: Boolean = false

      // Dummy MediaStore & AppStorage für SELECT
      private val selectMediaStore = object : IMediaStore {
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
            // Success → liefere Uri, Failure → null
            selectCalled = true
            return nextSelectResult.getOrNull()
         }

         override suspend fun loadBitmap(uri: Uri) = null
      }

      private val selectAppStorage = object : IAppStorage {
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

      // Dummy MediaStore & AppStorage für CAPTURE
      private val captureMediaStore = object : IMediaStore {
         override fun createSessionFolder(): String = "dummy"
         override fun createGroupedImageUri(groupName: String, filename: String?): Uri? = null

         override suspend fun saveImageToMediaStore(
            groupName: String,
            sourceUri: Uri
         ): Uri? {
            // einfach irgendeine MediaStore-Uri zurückgeben, damit der UseCase weitergeht
            captureCalled = true
            return "content://mediastore/dummy-capture".toUri()
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

      private val captureAppStorage = object : IAppStorage {
         override suspend fun convertImageUriToAppStorage(
            sourceUri: Uri,
            pathName: String
         ): Uri? {
            // Success → Uri, Failure → null
            return nextCaptureResult.getOrNull()
         }

         override suspend fun convertDrawableToAppStorage(
            drawableId: Int,
            pathName: String,
            uuidString: String?
         ): Uri? = null

         override suspend fun loadImageFromAppStorage(uri: Uri) = null

         override suspend fun deleteImageOnAppStorage(pathName: String) {}
      }

      // IImageUseCases-Properties – echte UseCases, aber mit obigen Fakes
      override val selectImage: ImageUcSelectGal =
         ImageUcSelectGal(selectMediaStore, selectAppStorage)

      override val captureImage: ImageUcCaptureCam =
         ImageUcCaptureCam(captureMediaStore, captureAppStorage)
   }

   /**
    * Dummy-NavHandler, weil ImageViewModel ihn im Konstruktor braucht.
    * Die Methoden kannst du an deine echte INavHandler-Signatur anpassen
    * oder hier einfach leer lassen, wenn sie im Test nicht genutzt werden.
    */
   private class FakeNavHandler : INavHandler {
      override fun push(destination: NavKey) {}
      override fun pop() {}
      override fun popToRootAndNavigate(rootDestination: NavKey) {}
   }
   //endregion
}
