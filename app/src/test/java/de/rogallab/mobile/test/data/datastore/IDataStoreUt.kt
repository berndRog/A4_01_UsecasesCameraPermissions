package de.rogallab.mobile.test.data.datastore

import app.cash.turbine.test
import de.rogallab.mobile.Globals
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
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
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestApplication::class) // <- nutzt deine TestApp
class IDataStoreUt: KoinTest {
   @get:Rule
   val tempDir = TemporaryFolder()
   @get:Rule
   val mainRule = MainDispatcherRule()

   private lateinit var _seed: Seed
   private lateinit var _dataStore: IDataStore
   private lateinit var _filePath: Path
   private lateinit var _seedPeople: List<Person>

   @Before
   fun setup() = runTest {
      // no logging during testing
      Globals.isInfo = false
      Globals.isDebug = false
      Globals.isVerbose = false

      stopKoin() // falls von anderen Tests übrig
      val testModule = defModulesTest(
         appHomePath = tempDir.root.absolutePath,
         ioDispatcher = mainRule.dispatcher() // StandardTestDispatcher als IO
      )
      val koinApp = startKoin { modules(testModule) }
      val koin = koinApp.koin
      _seed = koin.get<Seed>()
      _dataStore = koin.get<IDataStore>()

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
         stopKoin()
      }
   }

   @Test
   fun selectAll_ok() = runTest {
      // arrange
      val expected = _seedPeople
      // act: use turbine to access flow
      _dataStore.selectAll().test {
         val actual = awaitItem()
         assertEquals(_seedPeople.size, actual.size)
         assertContentEquals(_seedPeople, actual)
      }

   }

   @Test
   fun selectAllSortBy_ok() = runTest {
      // arrange
      val expected = _seedPeople
      // act/arrange
      _dataStore.selectAllSortedBy { it.firstName }.test {
         val actual = awaitItem()
         assertEquals(_seedPeople.size, actual.size)
         assertContentEquals(_seedPeople, actual)
      }
   }

   @Test
   fun selectWhere_ok() = runTest {
      // arrange
      val expected = _seedPeople.filter {
         it.email?.contains("gmail", true) ?: false
      }
      // act/assert
      _dataStore.selectWhere {
         it.email?.contains("gmail", true) ?: false
      }.test {
         val actual = awaitItem()
         assertContentEquals(expected, actual)
      }
   }

   @Test
   fun findById_ok() = runTest {
      // arrange
      val id = "01000000-0000-0000-0000-000000000000"
      val expected = _seedPeople.firstOrNull { person -> person.id == id }
      assertNotNull(expected)
      // act
      val actual = _dataStore.findById(id)
      // assert
      assertEquals(expected, actual)
   }

   @Test
   fun findBy_ok() = runTest {
      // arrange
      val phone = "02090"
      val expected = _seedPeople.firstOrNull { person ->
         person.phone?.contains(phone,true ) ?: false }
      assertNotNull(expected)
      // act
      val actual = _dataStore.findBy { person ->
         person.phone?.contains(phone,true ) ?: false }
      // assert
      assertEquals(expected, actual)
   }

   @Test
   fun insert_ok() = runTest{
      // arrange
      val person = Person(
         "Bernd", "Rogalla", "b-u.rogalla@ostfalia.de", null,
         id = "00000001-0000-0000-0000-000000000000")
      // act
      _dataStore.insert(person)
      // assert
      val actual = _dataStore.findById(person.id)
      assertEquals(person, actual)
   }

   @Test
   fun update_ok() = runTest {
      // arrange
      val id = "01000000-0000-0000-0000-000000000000"
      val person = _dataStore.findById(id)
      assertNotNull(person)
      // act
      val updated = person.copy(lastName ="Albers", email = "a.albers@gmx.de")
      _dataStore.update(updated)
      // assert
      val actual = _dataStore.findById(person.id)
      assertEquals(updated, actual)
   }

   @Test
   fun delete_ok() = runTest {
      // arrange
      val id = "01000000-0000-0000-0000-000000000000"
      val person = _dataStore.findById(id)
      assertNotNull(person)
      // act
      _dataStore.delete(person)
      // assert
      val actual = _dataStore.findById(person.id)
      assertNull(actual)
   }
}




//@RunWith(RobolectricTestRunner::class)
//@Config(sdk = [35], application = TestApplication::class) // <- nutzt deine TestApp
//class IDataStoreUt: KoinTest {
//
//   @get:Rule
//   val koinRule = KoinTestRule.create {
//      androidContext(ApplicationProvider.getApplicationContext<Context>())
//      modules(defModulesTest)   // deine Test-Module
//   }
//
//   // --- DI ---
//   private val _dataStore: IDataStore by inject()
//   private val _seed: Seed by inject()
//
//   // --- Test data ---
//   private lateinit var _seedPeople: List<Person>
//
//   // --- For cleanup ---
//   private var _filePathNio: Path? = null
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
//         try { Files.deleteIfExists(it) }
//         catch (_: Exception) { /* ignore */ }
//      }
//   }
//
//   @Test
//   fun selectAll_ok() {
//      // arrange
//      val expected = _seedPeople
//      // act
//      val actual = _dataStore.selectAll()
//      // assert
//      assertContentEquals(expected, actual)
//   }
//
//   @Test
//   fun selectAllSortBy_ok() {
//      // arrange
//      val expected = _seedPeople.sortedBy { it.firstName }
//      // act
//      val actual = _dataStore.selectAllSortedBy { it.firstName }
//      // assert
//      assertContentEquals(expected, actual)
//   }
//
//   @Test
//   fun selectWhere_ok() {
//      // arrange
//      val expected = _seedPeople.filter{
//         it.lastName?.contains("mann",true) ?: false }
//      // act
//      val actual = _dataStore.selectWhere {
//         it.lastName?.contains("mann",true) ?: false }
//      // assert
//      assertContentEquals(expected, actual)
//   }
//
//   @Test
//   fun findById_ok() {
//      // arrange
//      val id = "01000000-0000-0000-0000-000000000000"
//      val expected = _seedPeople.first { it.id == id  }
//      // act
//      val actual = _dataStore.findById(id)
//      // assert
//      assertEquals(expected, actual)
//   }
//
//   @Test
//   fun findBy_ok() {
//      // arrange
//      val firstName = "Arne"
//      val expected = _seedPeople.first { it.firstName == firstName }
//      // act
//      val actual = _dataStore.findBy { it.firstName == firstName }
//      // assert
//      assertEquals(expected, actual)
//   }
//
//
//   @Test
//   fun insert_ok() {
//      // arrange
//      val person = Person("Bernd", "Rogalla", "b-u.rogalla@ostfalia.de",
//         id = "00000001-0000-0000-0000-000000000000")
//      // act
//      _dataStore.insert(person)
//      // assert
//      assertEquals(person, _dataStore.findById(person.id))
//   }
//
//   @Test
//   fun update_ok() {
//      // arrange
//      val id = "01000000-0000-0000-0000-000000000000"
//      val person = requireNotNull(_dataStore.findById(id))
//      // act
//      val updated = person.copy(lastName ="Albers", email = "a.albers@gmx.de")
//      _dataStore.update(updated)
//      // assert
//      assertEquals(updated, _dataStore.findById(person.id))
//   }
//
//   @Test
//   fun delete_ok() {
//      // arrange
//      val id = "01000000-0000-0000-0000-000000000000"
//      val person = requireNotNull(_dataStore.findById(id))
//      // act
//      _dataStore.delete(person)
//      // assert
//      assertNull(_dataStore.findById(person.id))
//   }
//}