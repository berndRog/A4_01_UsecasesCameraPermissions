package de.rogallab.mobile.test.ui.people

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.navigation3.runtime.NavKey
import app.cash.turbine.test
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.domain.IAppMediaStore
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IImageUseCases
import de.rogallab.mobile.domain.IPeopleUseCases
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person
import de.rogallab.mobile.domain.usecases.images.ImageUcCaptureCam
import de.rogallab.mobile.domain.usecases.images.ImageUcDeleteLocal
import de.rogallab.mobile.domain.usecases.images.ImageUcSelectGal
import de.rogallab.mobile.domain.usecases.images.ImageUseCases
import de.rogallab.mobile.test.MainDispatcherRule
import de.rogallab.mobile.test.TestApplication
import de.rogallab.mobile.test.di.defModulesTest
import de.rogallab.mobile.test.domain.utilities.setupConsoleLogger
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.PeopleList
import de.rogallab.mobile.ui.people.PersonIntent
import de.rogallab.mobile.ui.people.PersonViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Robolectric integration tests for PersonViewModel using Turbine to assert StateFlow emissions.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelIntegrationTest : KoinTest {

   @get:Rule
   val tempDir = TemporaryFolder()

   @get:Rule
   val mainRule = MainDispatcherRule()

   // parameters for tests
   private val directoryName = "test"
   private val fileName = "people.json"

   private lateinit var _context: Context
   private lateinit var _useCases: IPeopleUseCases
   private lateinit var _seed: Seed
   private lateinit var _dataStore: IDataStore
   private lateinit var _repository: IPersonRepository
   private lateinit var _filePath: Path
   private lateinit var _seedPeople: List<Person>
   private lateinit var _viewModel: PersonViewModel

   @Before
   fun setUp() = runTest {

      // Configure logging (can be turned off if too noisy)
      de.rogallab.mobile.Globals.isInfo = true
      de.rogallab.mobile.Globals.isDebug = true
      de.rogallab.mobile.Globals.isVerbose = false
      de.rogallab.mobile.Globals.isComp = false
      setupConsoleLogger()

      // ensure clean Koin graph per test run
      stopKoin()

      // Additional test module to override image-related dependencies
      val imageFakeModule = module {
         // 1) Fake low-level dependencies so no real MediaStore / files are touched
         single<IAppMediaStore> { FakeAppMediaStore() }
         single<IAppStorage> { FakeAppStorage() }

         // 2) Real image use case classes, but injected with the fake low-level deps
         single { ImageUcCaptureCam(get(), get()) }      // (IAppMediaStore, IAppStorage)
         single { ImageUcSelectGal(get(), get()) }       // (IAppMediaStore, IAppStorage)
         single { ImageUcDeleteLocal(get()) }            // (IAppStorage)

         // 3) Aggregator for all image use cases, as required by IImageUseCases
         single<IImageUseCases> {
            ImageUseCases(
               captureImage = get(),
               selectImage = get(),
               deleteImageLocal = get()
            )
         }
      }

      // Boot Koin graph exactly like your other tests + image fakes
      val koinApp = startKoin {
         modules(
            defModulesTest(
               appHomeName = tempDir.root.absolutePath,
               directoryName = directoryName,
               fileName = fileName,
               ioDispatcher = mainRule.dispatcher()
            ),
            imageFakeModule
         )
      }

      val koin = koinApp.koin
      _seed = koin.get<Seed>()
      _dataStore = koin.get<IDataStore>()
      _repository = koin.get<IPersonRepository>()
      _useCases = koin.get<IPeopleUseCases>()
      val navKey: NavKey = PeopleList
      val navHandler: INavHandler = koin.get { parametersOf(navKey) }
      _viewModel = koin.get { parametersOf(navHandler) }

      _filePath = _dataStore.filePath
      _dataStore.initialize()

      // Create seed data after Koin has started
      _seedPeople = _seed.people.toList()

      // Trigger lazy ViewModel creation so that init { } runs and the initial DataStore/Room flow starts.
      _viewModel
      // Let all coroutines (init + initial fetch) complete before tests assert on state.
      advanceUntilIdle()
   }

   @After
   fun tearDown() {
      try {
         Files.deleteIfExists(_filePath)
      } catch (_: Throwable) {
         // ignore cleanup errors
      } finally {
         // Clean up Koin as well
         stopKoin()
      }
   }

   // ------------------------------------------------------------------------
   // Initial load: the People list is populated from Seed after ViewModel init.
   // ------------------------------------------------------------------------
   @Test
   fun initial_list_matches_seed_after_init() = runTest {
      val state = _viewModel.peopleUiStateFlow.value

      // We do not care about transient loading states here; we only assert the stable result.
      assertFalse(state.isLoading, "State should not be loading after initial fetch has completed")
      assertEquals(
         _seedPeople,
         state.people,
         "People list after init should match seeded data"
      )
   }

   // ------------------------------------------------------------------------
   // FetchById: loads a single person into PersonUiState
   // ------------------------------------------------------------------------
   @Test
   fun fetchById_loads_person() = runTest {
      // The list is already loaded in setUp() via ViewModel init.
      val id = _viewModel.peopleUiStateFlow.value.people.first().id

      _viewModel.personUiStateFlow.test {

         // (1) Initial PersonUiState (usually a template/empty person)
         val initial = awaitItem()
         // No loading flag on PersonUiState, so we only assert structural expectations if needed.

         // Trigger FetchById via MVI intent
         _viewModel.handlePersonIntent(PersonIntent.FetchById(id))

         // (2) Updated PersonUiState for the requested person
         val loaded = awaitItem()
         assertEquals(id, loaded.person.id, "PersonUiState should contain the requested person")

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // Create: increases list size and contains the new person
   // ------------------------------------------------------------------------

   @Test
   fun create_increases_list() = runTest {
      // Precondition: initial list is already loaded by init{}.
      val sizeBefore = _viewModel.peopleUiStateFlow.value.people.size
      val firstName = "Bernd"
      val lastName = "Rogalla"

      _viewModel.peopleUiStateFlow.test {

         // (1) Baseline state before create (already contains the initial list)
         val baseline = awaitItem()
         assertFalse(baseline.isLoading, "Baseline should not be loading")
         assertEquals(sizeBefore, baseline.people.size, "Baseline size should match initial size")

         // Prepare PersonUiState (does not change PeopleUiState yet)
         _viewModel.handlePersonIntent(PersonIntent.Clear)
         _viewModel.handlePersonIntent(PersonIntent.FirstNameChange(firstName))
         _viewModel.handlePersonIntent(PersonIntent.LastNameChange(lastName))

         // Trigger create() (flow will emit an updated list)
         _viewModel.handlePersonIntent(PersonIntent.Create)

         // (2) Updated state with the newly created person
         val updated = awaitItem()
         assertFalse(updated.isLoading, "Updated state after create should not be loading")

         val sizeIncreasedByOne = updated.people.size == sizeBefore + 1
         val found = updated.people.any { it.firstName == firstName && it.lastName == lastName }

         assertTrue(sizeIncreasedByOne, "People list size should be increased by one")
         assertTrue(found, "Newly created person should be present in the list")

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // Update: list contains the person with updated last name
   // ------------------------------------------------------------------------

   @Test
   fun update_changes_list() = runTest {
      // Precondition: initial list is already loaded by init{}.
      val id = _viewModel.peopleUiStateFlow.value.people.first().id
      val newLastName = "Albers"

      // Load the person into PersonUiState so we can edit it
      _viewModel.handlePersonIntent(PersonIntent.FetchById(id))
      advanceUntilIdle()

      _viewModel.peopleUiStateFlow.test {

         // (1) Baseline state before update
         val baseline = awaitItem()
         assertFalse(baseline.isLoading, "Baseline should not be loading")

         // Trigger update: change last name and call Update
         _viewModel.handlePersonIntent(PersonIntent.LastNameChange(newLastName))
         _viewModel.handlePersonIntent(PersonIntent.Update)

         // (2) Updated state where the person should have the new last name
         val updatedState = awaitItem()
         assertFalse(updatedState.isLoading, "Updated state after update should not be loading")

         val updated = updatedState.people.first { it.id == id }
         assertEquals(newLastName, updated.lastName, "Updated person should have the new last name")

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // Update: list size remains same and PersonUiState is consistent
   // ------------------------------------------------------------------------

   @Test
   fun update_keeps_list_size_and_updates_person_in_personUiState_and_peopleUiState() = runTest {
      // Precondition: initial list is already loaded by init{}.
      val initialPeopleState = _viewModel.peopleUiStateFlow.value
      val initialSize = initialPeopleState.people.size

      val original = initialPeopleState.people.first()
      val id = original.id
      val newLastName = "UpdatedLastName"

      // Load the person into PersonUiState
      _viewModel.handlePersonIntent(PersonIntent.FetchById(id))
      advanceUntilIdle()

      val personStateBeforeUpdate = _viewModel.personUiStateFlow.value
      assertEquals(id, personStateBeforeUpdate.person.id, "PersonUiState should hold the correct person before update")
      assertEquals(original.lastName, personStateBeforeUpdate.person.lastName)

      _viewModel.peopleUiStateFlow.test {

         // (1) Baseline state, list size should match initial size
         val baseline = awaitItem()
         assertEquals(initialSize, baseline.people.size, "Baseline list size should match initial size")

         // Trigger update: change last name and call Update
         _viewModel.handlePersonIntent(PersonIntent.LastNameChange(newLastName))
         _viewModel.handlePersonIntent(PersonIntent.Update)

         // (2) Updated list with the same size but modified person
         val updatedState = awaitItem()
         assertFalse(updatedState.isLoading, "Updated state should not be loading")
         assertEquals(initialSize, updatedState.people.size, "List size should remain unchanged after update")

         // Person in people list must be updated
         val updatedFromList = updatedState.people.first { it.id == id }
         assertEquals(original.firstName, updatedFromList.firstName, "First name should not change")
         assertEquals(newLastName, updatedFromList.lastName, "Last name should be updated")

         // PersonUiState must also be updated
         val personStateAfterUpdate = _viewModel.personUiStateFlow.value
         assertEquals(id, personStateAfterUpdate.person.id)
         assertEquals(newLastName, personStateAfterUpdate.person.lastName)

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // Remove (without Undo): list size is reduced and victim is gone
   // ------------------------------------------------------------------------

   @Test
   fun remove_reduces_list() = runTest {
      // Precondition: initial list is already loaded by init{}.
      val initialState = _viewModel.peopleUiStateFlow.value
      val victim = initialState.people.first()
      val victimId = victim.id
      val initialSize = initialState.people.size

      _viewModel.peopleUiStateFlow.test {

         // (1) Baseline state
         val baseline = awaitItem()
         assertEquals(initialSize, baseline.people.size, "Baseline list size should match initial size")

         // Trigger remove()
         _viewModel.handlePersonIntent(PersonIntent.Remove(victim))

         // (2) Updated state with one element removed
         val updated = awaitItem()
         assertFalse(updated.isLoading, "Updated state should not be loading")
         assertEquals(initialSize - 1, updated.people.size, "List size should be reduced by one after remove")
         val isDeleted = updated.people.none { it.id == victimId }
         assertTrue(isDeleted, "Removed person should no longer be present in the list")

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // RemoveUndo: optimistic UI remove only (no persistence, no loading)
   // ------------------------------------------------------------------------

   @Test
   fun removeUndo_temporarily_removes_person_from_list() = runTest {
      // Precondition: initial list is already loaded by init{}.
      val initialState = _viewModel.peopleUiStateFlow.value
      val initialSize = initialState.people.size
      val victim = initialState.people.first()
      val victimId = victim.id

      _viewModel.peopleUiStateFlow.test {

         // (1) Baseline state
         val baseline = awaitItem()
         assertEquals(initialSize, baseline.people.size, "Baseline size should match initial size")

         // Trigger optimistic remove with undo buffer
         _viewModel.handlePersonIntent(PersonIntent.RemoveUndo(victim))

         // (2) UI-only removed state (no loading, no fetch)
         val afterRemove = awaitItem()
         assertEquals(initialSize - 1, afterRemove.people.size, "List size should be reduced by one")
         assertTrue(afterRemove.people.none { it.id == victimId }, "Victim should be removed from the UI list")
         assertEquals(null, afterRemove.restoredPersonId, "restoredPersonId should not be set yet")

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // Undo: restores removed person and sets restoredPersonId
   // ------------------------------------------------------------------------

   @Test
   fun undoRestores_removed_person_and_sets_restoredPersonId() = runTest {
      // Precondition: initial list is already loaded by init{}.
      val initialState = _viewModel.peopleUiStateFlow.value
      val initialSize = initialState.people.size
      val victim = initialState.people.first()
      val victimId = victim.id

      // Apply RemoveUndo before collecting the flow
      _viewModel.handlePersonIntent(PersonIntent.RemoveUndo(victim))
      advanceUntilIdle()

      _viewModel.peopleUiStateFlow.test {

         // (1) State after RemoveUndo
         val afterRemove = awaitItem()
         assertEquals(initialSize - 1, afterRemove.people.size)
         assertTrue(afterRemove.people.none { it.id == victimId })

         // Trigger Undo → restores item from internal undo buffer
         _viewModel.handlePersonIntent(PersonIntent.Undo)

         // (2) State after Undo
         val afterUndo = awaitItem()
         assertEquals(initialSize, afterUndo.people.size, "List size should be restored")
         assertTrue(afterUndo.people.any { it.id == victimId }, "Victim should be restored in the list")
         assertEquals(victimId, afterUndo.restoredPersonId, "restoredPersonId should point to restored person")

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // Restored: clears restoredPersonId while keeping list unchanged
   // ------------------------------------------------------------------------

   @Test
   fun restored_clears_restoredPersonId_but_keeps_list_unchanged() = runTest {
      // Precondition: initial list is loaded, then RemoveUndo + Undo are applied.
      val initialState = _viewModel.peopleUiStateFlow.value
      val initialSize = initialState.people.size
      val victim = initialState.people.first()
      val victimId = victim.id

      _viewModel.handlePersonIntent(PersonIntent.RemoveUndo(victim))
      _viewModel.handlePersonIntent(PersonIntent.Undo)
      advanceUntilIdle()

      _viewModel.peopleUiStateFlow.test {

         // (1) State after Undo (restoredPersonId is set)
         val afterUndo = awaitItem()
         assertEquals(victimId, afterUndo.restoredPersonId)
         assertEquals(initialSize, afterUndo.people.size)

         // Trigger Restored once UI has scrolled to the restored item
         _viewModel.handlePersonIntent(PersonIntent.Restored)

         // (2) Restored state:
         //   restoredPersonId = null
         //   people list unchanged
         val afterRestored = awaitItem()
         assertEquals(null, afterRestored.restoredPersonId, "restoredPersonId should be cleared")
         assertEquals(initialSize, afterRestored.people.size, "List size should remain unchanged")
         assertTrue(afterRestored.people.any { it.id == victimId }, "Restored person must still be in the list")

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // Full RemoveUndo → Undo → Restored sequence in a single flow
   // ------------------------------------------------------------------------

   @Test
   fun removeUndo_then_undo_restores_person_and_clears_restored_flag() = runTest {
      // Precondition: initial list is already loaded by init{}.
      val initialState = _viewModel.peopleUiStateFlow.value
      val initialList = initialState.people
      val initialSize = initialList.size
      val index = if (initialSize > 1) 1 else 0
      val victim = initialList[index]

      _viewModel.peopleUiStateFlow.test {

         // (1) Baseline state
         val baseline = awaitItem()
         assertEquals(initialSize, baseline.people.size, "Baseline list size should match initial size")

         // Step 1: RemoveUndo
         _viewModel.handlePersonIntent(PersonIntent.RemoveUndo(victim))

         // (2) State after RemoveUndo (victim removed from list)
         val afterRemove = awaitItem()
         val afterRemoveList = afterRemove.people
         assertEquals(initialSize - 1, afterRemoveList.size, "List size should be reduced by one")
         assertTrue(afterRemoveList.none { it.id == victim.id }, "Victim should be removed")

         // Step 2: Undo
         _viewModel.handlePersonIntent(PersonIntent.Undo)

         // (3) State after Undo (victim restored and restoredPersonId set)
         val afterUndo = awaitItem()
         val afterUndoList = afterUndo.people
         assertEquals(initialSize, afterUndoList.size, "List size should be restored")
         assertEquals(victim.id, afterUndoList[index].id, "Victim should be restored at the same index")
         assertEquals(victim.id, afterUndo.restoredPersonId, "restoredPersonId should point to restored person")

         // Step 3: Restored (UI acknowledges scroll completion)
         _viewModel.handlePersonIntent(PersonIntent.Restored)

         // (4) Final state (restoredPersonId cleared, list unchanged)
         val final = awaitItem()
         val finalList = final.people
         assertEquals(initialSize, finalList.size, "List size should remain unchanged")
         assertEquals(victim.id, finalList[index].id, "Victim should still be present in the list")
         assertEquals(null, final.restoredPersonId, "restoredPersonId should be cleared")

         cancelAndIgnoreRemainingEvents()
      }
   }

   // ------------------------------------------------------------------------
   // Fakes for image-related dependencies (IAppMediaStore, IAppStorage)
   // ------------------------------------------------------------------------

   /**
    * Fake implementation of [IAppMediaStore] for Robolectric Person tests.
    * We do not want any real MediaStore or bitmap work here; all methods are stubs.
    */
   private class FakeAppMediaStore : IAppMediaStore {
      override suspend fun createGroupedImageUri(
         groupName: String,
         filename: String?
      ): Uri? = null

      override suspend fun saveImageToMediaStore(
         groupName: String,
         sourceUri: Uri
      ): Uri? = null

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
    * Fake implementation of [IAppStorage] for Robolectric Person tests.
    * All methods are stubs; the PersonViewModel tests do not rely on real image storage.
    */
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

      override suspend fun loadImage(uri: Uri): Bitmap? = null

      override suspend fun deleteImage(pathName: String) {}
   }
}
