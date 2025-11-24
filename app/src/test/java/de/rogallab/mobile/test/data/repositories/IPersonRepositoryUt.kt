package de.rogallab.mobile.test.data.repositories

import app.cash.turbine.test
import de.rogallab.mobile.Globals
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person
import de.rogallab.mobile.test.MainDispatcherRule
import de.rogallab.mobile.test.TestApplication
import de.rogallab.mobile.test.di.defModulesTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

// problems with java version 17 and android sdk 36
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class) // keine MainApplication!
class IPersonRepositoryUt : KoinTest {
   @get:Rule
   val tempDir = TemporaryFolder()
   @get:Rule
   val mainRule = MainDispatcherRule()

   private lateinit var _seed: Seed
   private lateinit var _dataStore: IDataStore
   private lateinit var _repository: IPersonRepository
   private lateinit var _filePath: Path
   private lateinit var _seedPeople: List<Person>

   @Before
   fun setup() = runTest {
      // no logging during testing
      Globals.isInfo = false
      Globals.isDebug = false
      Globals.isVerbose = false

      GlobalContext.stopKoin() // falls von anderen Tests übrig
      val testModule = defModulesTest(
         appHomePath = tempDir.root.absolutePath,
         ioDispatcher = mainRule.dispatcher() // StandardTestDispatcher als IO
      )
      val koinApp = GlobalContext.startKoin { modules(testModule) }
      val koin = koinApp.koin
      _seed = koin.get<Seed>()
      _dataStore = koin.get<IDataStore>()
      _repository = koin.get<IPersonRepository>()

      // store filepath
      _filePath = _dataStore.filePath
      Files.deleteIfExists(_filePath)

      // read people into dataStore
      _dataStore.initialize()

      // expected
      _seedPeople = _seed.people
   }

   @After
   fun tearDown() {
      try {
         Files.delete(_filePath.fileName)
      }
      catch (_e: IOException) {
      }
      finally {
         GlobalContext.stopKoin()
      }
   }



   @Test
   fun getAllSortByUt_ok() = runTest {
      // arrange
      val expected = _seedPeople.sortedBy { it.firstName }
      // act / assert
      _repository.getAllSorted().test {
         awaitItem()
            .onSuccess { assertContentEquals(expected, it.toMutableList()) }
            .onFailure { fail(it.message) }
      }
   }

   @Test
   fun insert_emitsUpdateFlow() = runTest {
      // arrange
      val newPerson = Person(
         "Bernd", "Rogalla", "b-u.rogalla@ostfalia.de", null,
          id = "00090001-0000-0000-0000-000000000001"
      )

      // act / assert: subscribe to flow, perform insert, expect another emission containing the new person
      _repository.getAllSorted().test {
         // consume initial emission
         val initial = awaitItem()
         initial.onFailure { fail(it.message) }
         initial.onSuccess { result ->
            assertTrue(result.size == 26)
         }

         // perform insert and assert success
         _repository.create(newPerson)
            .onFailure { fail(it.message) }
            .onSuccess { assertEquals(Unit, it) }

         // await next emission and assert it contains the inserted person
         val updated = awaitItem()
         updated.onFailure { fail(it.message) }
         updated.onSuccess { result ->
            assertTrue(result.any { p -> p.id == newPerson.id && p == newPerson }, "Inserted person not present in emitted list")
         }

         cancelAndIgnoreRemainingEvents()
      }
   }

   @Test
   fun findByIdUt_ok() = runTest {
      // arrange
      val id = "01000000-0000-0000-0000-000000000000"
      val expected = _seedPeople.firstOrNull { person -> person.id == id  }
      assertNotNull(expected)
      // act / assert
      _repository.findById(id)
         .onSuccess { assertEquals(expected, it)  }
         .onFailure { fail(it.message) }
   }

   @Test
   fun insertUt_ok() = runTest {
      // arrange
      val person = Person(
         "Bernd", "Rogalla", "b-u.rogalla@ostfalia.de", null,
         id = "00090001-0000-0000-0000-000000000000")
      // act
      _repository.create(person)
         .onSuccess { assertEquals(Unit, it) }
         .onFailure { fail(it.message) }
      // assert
      _repository.findById(person.id)
         .onSuccess { assertEquals(person, it) }
         .onFailure { fail(it.message) }
   }

   @Test
   fun updateUt_ok() = runTest {
      // arrange
      val id = "01000000-0000-0000-0000-000000000000"
      var person: Person? = null
      _repository.findById(id)
         .onSuccess { person = it }
         .onFailure { t -> fail(t.message) }
      assertNotNull(person)
      // act
      val updated = person.copy(lastName ="Albers", email = "a.albers@gmx.de")
      _repository.update(updated)
         .onSuccess { assertEquals(Unit, it) }
         .onFailure { fail(it.message) }
      // assert
      _repository.findById(person.id)
         .onSuccess { assertEquals(updated, it) }
         .onFailure { fail(it.message) }
   }

   @Test
   fun deleteUt_ok() = runTest {
      // arrange
      val id = "01000000-0000-0000-0000-000000000000"
      val person = _dataStore.findById(id)
      assertNotNull(person)
      // act
      _repository.remove(person)
         .onSuccess { assertEquals(Unit, it) }
         .onFailure { fail(it.message) }
      // assert
      _repository.findById(person.id)
         .onSuccess { actual -> assertNull(actual) }
         .onFailure { fail(it.message) }
   }


}


//@RunWith(RobolectricTestRunner::class)
//@Config(sdk = [35], application = TestApplication::class) // keine MainApplication!
//class IPersonRepositoryUt : KoinTest {
//
//   @get:Rule
//   val koinRule = KoinTestRule.create {
//      androidContext(ApplicationProvider.getApplicationContext<Context>())
//      modules(defModulesTest)   // deine Test-Module
//   }
//
//   // --- DI ---
//   private val _dataStore: IDataStore by inject()
//   private val _repository: IPersonRepository by inject()
//   private val _seed: Seed by inject()
//
//   // --- Test data ---
//   private lateinit var _seedPeople:List<Person>
//
//   // --- For cleanup ---
//   private var _filePathNio: Path? = null
//
//
//   @Before
//   fun setup() {
//      // no logging during testing
//      Globals.isInfo = false
//      Globals.isDebug = false
//      Globals.isVerbose = false
//      Globals.isComp = false
//
//      // create seed after Koin has started
//      _seedPeople = _seed.people.toList()
//
//      // capture file path
//      _filePathNio = _dataStore.filePath
//
//      // Prepare the test store
//      _dataStore.initialize()
//   }
//
//   @After
//   fun tearDown() {
//      // Delete the file created during the test (whichever type you use)
//      _filePathNio?.let {
//         try {
//            Files.deleteIfExists(it)
//         }
//         catch (_: Exception) { /* ignore */
//         }
//      }
//   }
//
//   @Test
//   fun getAllUt_ok() {
//      val expected = _seedPeople
//      _repository.getAll()
//         .onSuccess { actual -> assertContentEquals(expected, actual) }
//         .onFailure { fail(it.message) }
//   }
//
//   @Test
//   fun getAllSortByUt_ok() {
//      // arrange
//      val expected = _seedPeople.sortedBy { it.firstName }
//      // act / assert
//      _repository.getAllSortedBy { it.firstName }
//         .onSuccess { actual -> assertContentEquals(expected, actual) }
//         .onFailure { fail(it.message) }
//   }
//
//   @Test
//   fun getWhereUt_ok() {
//      // arrange
//      val expected = _seedPeople.filter { it.lastName.contains("mann", true) }
//      // act / assert  --> Hoffmann
//      _repository.getWhere { it.lastName.contains("mann", true) }
//         .onSuccess { actual -> assertContentEquals(expected, actual) }
//         .onFailure { fail(it.message) }
//   }
//
//   @Test
//   fun findByIdUt_ok() {
//      // arrange
//      val id = "01000000-0000-0000-0000-000000000000"
//      val expected = _seedPeople.firstOrNull { person -> person.id == id }
//      // act / assert
//      val result = _repository.findById(id)
//      assert(result.isSuccess)
//      assertEquals(expected, result.getOrThrow())
//   }
//
//   @Test
//   fun findByUt_ok() {
//      // arrange
//      val expected = _seedPeople.firstOrNull { person ->
//         person.lastName.contains("mann", true)
//      }
//      // act / assert
//      val result = _repository.findBy { it.lastName.contains("mann", true) }
//      assert(result.isSuccess)
//      assertEquals(expected, result.getOrThrow())
//   }
//
//   @Test
//   fun insertUt_ok() {
//      // arrange
//      val person = Person(
//         "Bernd", "Rogalla", id = "00000001-0000-0000-0000-000000000000")
//      // act
//      val createResult = _repository.create(person)
//      assert(createResult.isSuccess)
//      // assert
//      val result = _repository.findById(person.id)
//      assert(result.isSuccess)
//      assertEquals(person, result.getOrThrow())
//   }
//
//   @Test
//   fun updateUt_ok() {
//      // arrange
//      val id = "01000000-0000-0000-0000-000000000000"
//      var person = requireNotNull(_repository.findById(id).getOrThrow())
//      // act
//      val updated = person.copy(lastName = "Albers")
//      val updateResult = _repository.update(updated)
//      assert(updateResult.isSuccess)
//      // assert
//      val result = _repository.findById(id)
//      assert(result.isSuccess)
//      assertEquals(updated, result.getOrThrow())
//   }
//
//   @Test
//   fun deleteUt_ok() {
//      // arrange
//      val id = "01000000-0000-0000-0000-000000000000"
//      val person = requireNotNull(_repository.findById(id).getOrThrow())
//      // act
//      val result = _repository.remove(person)
//      assert(result.isSuccess)
//      // assert
//      assertNull(_repository.findById(person.id).getOrThrow())
//   }
//}
