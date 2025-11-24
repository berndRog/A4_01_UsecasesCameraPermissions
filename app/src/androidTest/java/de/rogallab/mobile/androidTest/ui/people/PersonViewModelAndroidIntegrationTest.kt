package de.rogallab.mobile.androidTest.ui.people

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.rogallab.mobile.Globals
import de.rogallab.mobile.androidTest.di.defModulesAndroidTest
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.domain.entities.Person
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.PeopleList
import de.rogallab.mobile.ui.people.PeopleIntent
import de.rogallab.mobile.ui.people.PersonIntent
import de.rogallab.mobile.ui.people.PersonViewModel
import de.rogallab.mobile.androidTest.MainDispatcherRule
import de.rogallab.mobile.androidTest.domain.utilities.setupConsoleLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelAndroidIntegrationTest : KoinTest {

   @get:Rule
   val mainDispatcherRule = MainDispatcherRule()

   // DI
   private val _dataStore: IDataStore by inject()
   private val _navHandler: INavHandler by inject { parametersOf(PeopleList) }
   private val _viewModel: PersonViewModel by inject { parametersOf(_navHandler) }
   private val _seed: Seed by inject()

   private lateinit var _seedPeople: List<Person>
   private lateinit var _filePath: Path

   @Before
   fun setUp() = kotlinx.coroutines.runBlocking {

      stopKoin()
      startKoin {
         androidContext(InstrumentationRegistry.getInstrumentation().targetContext)
         modules(defModulesAndroidTest(
            appHomePath = "",
            ioDispatcher = mainDispatcherRule.testDispatcher
         ))
      }

      // reduce noise
      Globals.isInfo = false
      Globals.isDebug = false
      Globals.isVerbose = false
      Globals.isComp = false
      setupConsoleLogger()

      _seedPeople = _seed.people.toList()

      _filePath = _dataStore.filePath
      _dataStore.initialize()   // suspend

      // init { fetch() } im ViewModel ist bereits gelaufen,
      // aber die Coroutine hängt am Test-Dispatcher -> alles abarbeiten:
      mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
   }

   @After
   fun tearDown() {
      try {
         Files.deleteIfExists(_filePath)
      } catch (_: Throwable) { }
   }

   // ------------------------------------------------------------
   // 1) Initial load via init { fetch() }
   // ------------------------------------------------------------
   @Test
   fun initial_fetch_in_init_loads_list() = runTest {
      // Alle laufenden Coroutines (fetch) abarbeiten
      advanceUntilIdle()

      val state = _viewModel.peopleUiStateFlow.value
      assertEquals(
         _seedPeople.size,
         state.people.size,
         "Initial fetch in init{} should load all seeded people"
      )
      assertEquals(false, state.isLoading, "After initial load isLoading should be false")
   }

   // ------------------------------------------------------------
   // 2) Explicit Fetch intent (PeopleIntent.Fetch)
   // ------------------------------------------------------------
   @Test
   fun fetch_emits_loaded_list() = runTest {
      // initialer Zustand nach init{} (sicherheitshalber abarbeiten)
      advanceUntilIdle()

      // act: expliziter Fetch
      _viewModel.handlePeopleIntent(PeopleIntent.Fetch)
      advanceUntilIdle()

      val state = _viewModel.peopleUiStateFlow.value
      assertEquals(_seedPeople.size, state.people.size, "Fetch should load all seeded people")
      assertEquals(false, state.isLoading, "After fetch isLoading should be false")
   }

   // ------------------------------------------------------------
   // 3) FetchById loads person into PersonUiState
   // ------------------------------------------------------------
   @Test
   fun fetchById_loads_person() = runTest {
      // ensure list is loaded
      advanceUntilIdle()
      val expected = _viewModel.peopleUiStateFlow.value.people.first()
      val id = expected.id

      // act
      _viewModel.handlePersonIntent(PersonIntent.FetchById(id))
      advanceUntilIdle()

      val personState = _viewModel.personUiStateFlow.value
      assertEquals(expected.id, personState.person.id)
      assertEquals(expected.firstName, personState.person.firstName)
      assertEquals(expected.lastName, personState.person.lastName)
   }

   // ------------------------------------------------------------
   // 4) Create increases list size (Room/DataStore reactive flow)
   // ------------------------------------------------------------
   @Test
   fun create_increases_list() = runTest {
      // ensure initial load
      advanceUntilIdle()
      val stateBefore = _viewModel.peopleUiStateFlow.value
      val sizeBefore = stateBefore.people.size

      val firstName = "Bernd"
      val lastName = "Rogalla"

      // act: build new person in PersonUiState and call Create
      _viewModel.handlePersonIntent(PersonIntent.Clear)
      _viewModel.handlePersonIntent(PersonIntent.FirstNameChange(firstName))
      _viewModel.handlePersonIntent(PersonIntent.LastNameChange(lastName))
      _viewModel.handlePersonIntent(PersonIntent.Create)

      // DataStore/Room flow + fetch collector sollen alles abarbeiten
      advanceUntilIdle()

      val stateAfter = _viewModel.peopleUiStateFlow.value
      assertEquals(sizeBefore + 1, stateAfter.people.size, "List size should be increased by one")
      assertTrue(
         stateAfter.people.any { it.firstName == firstName && it.lastName == lastName },
         "Newly created person should appear in people list"
      )
   }

   // ------------------------------------------------------------
   // 5) Update changes person in list (and keeps size)
   // ------------------------------------------------------------
   @Test
   fun update_changes_person_in_list() = runTest {
      advanceUntilIdle()
      val before = _viewModel.peopleUiStateFlow.value
      val beforeSize = before.people.size
      val original = before.people.first()
      val id = original.id
      val newLastName = "UpdatedLastName"

      // load person into PersonUiState
      _viewModel.handlePersonIntent(PersonIntent.FetchById(id))
      advanceUntilIdle()

      // change and update
      _viewModel.handlePersonIntent(PersonIntent.LastNameChange(newLastName))
      _viewModel.handlePersonIntent(PersonIntent.Update)
      advanceUntilIdle()

      val after = _viewModel.peopleUiStateFlow.value
      assertEquals(beforeSize, after.people.size, "Update must not change list size")
      val updated = after.people.first { it.id == id }
      assertEquals(original.firstName, updated.firstName)
      assertEquals(newLastName, updated.lastName)
   }

   // ------------------------------------------------------------
   // 6) Remove reduces list size
   // ------------------------------------------------------------
   @Test
   fun remove_reduces_list() = runTest {
      advanceUntilIdle()
      val before = _viewModel.peopleUiStateFlow.value
      val beforeSize = before.people.size
      val victim = before.people.first()

      _viewModel.handlePersonIntent(PersonIntent.Remove(victim))
      advanceUntilIdle()

      val after = _viewModel.peopleUiStateFlow.value
      assertEquals(beforeSize - 1, after.people.size, "List size should be reduced by one")
      assertTrue(after.people.none { it.id == victim.id }, "Removed person must not be in list")
   }

   // ------------------------------------------------------------
   // 7) RemoveUndo → removes from UI list but keeps undo buffer
   // ------------------------------------------------------------
   @Test
   fun removeUndo_temporarily_removes_person_from_list() = runTest {
      advanceUntilIdle()
      val initial = _viewModel.peopleUiStateFlow.value
      val initialSize = initial.people.size
      val victim = initial.people.first()

      _viewModel.handlePersonIntent(PersonIntent.RemoveUndo(victim))
      // removeUndo() manipulates UI synchron synchronously, kein extra Flow nötig

      val afterRemove = _viewModel.peopleUiStateFlow.value
      assertEquals(initialSize - 1, afterRemove.people.size, "List size should be reduced temporarily")
      assertTrue(afterRemove.people.none { it.id == victim.id })
      // restoredPersonId wird erst bei Undo gesetzt
      assertEquals(null, afterRemove.restoredPersonId)
   }

   // ------------------------------------------------------------
   // 8) Undo restores person and sets restoredPersonId
   // ------------------------------------------------------------
   @Test
   fun undoRestores_removed_person_and_sets_restoredPersonId() = runTest {
      advanceUntilIdle()
      val initial = _viewModel.peopleUiStateFlow.value
      val initialSize = initial.people.size
      val victim = initial.people.first()
      val victimId = victim.id

      _viewModel.handlePersonIntent(PersonIntent.RemoveUndo(victim))
      val afterRemove = _viewModel.peopleUiStateFlow.value
      assertEquals(initialSize - 1, afterRemove.people.size)

      // act: undo
      _viewModel.handlePersonIntent(PersonIntent.Undo)

      val afterUndo = _viewModel.peopleUiStateFlow.value
      assertEquals(initialSize, afterUndo.people.size, "List size should be restored")
      assertTrue(afterUndo.people.any { it.id == victimId })
      assertEquals(victimId, afterUndo.restoredPersonId, "restoredPersonId should mark restored item")
   }

   // ------------------------------------------------------------
   // 9) Restored clears restoredPersonId but keeps list untouched
   // ------------------------------------------------------------
   @Test
   fun restored_clears_restoredPersonId_but_keeps_list_unchanged() = runTest {
      advanceUntilIdle()
      val initial = _viewModel.peopleUiStateFlow.value
      val initialSize = initial.people.size
      val victim = initial.people.first()
      val victimId = victim.id

      _viewModel.handlePersonIntent(PersonIntent.RemoveUndo(victim))
      _viewModel.handlePersonIntent(PersonIntent.Undo)

      val afterUndo = _viewModel.peopleUiStateFlow.value
      assertEquals(victimId, afterUndo.restoredPersonId)
      assertEquals(initialSize, afterUndo.people.size)

      // act: UI acknowledges scroll
      _viewModel.handlePersonIntent(PersonIntent.Restored)

      val finalState = _viewModel.peopleUiStateFlow.value
      assertEquals(null, finalState.restoredPersonId, "restoredPersonId should be cleared")
      assertEquals(initialSize, finalState.people.size)
      assertTrue(finalState.people.any { it.id == victimId })
   }
}
