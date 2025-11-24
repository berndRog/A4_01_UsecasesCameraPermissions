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

// problems with java version 17 and android sdk 36
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class) // <- nutzt deine TestApp
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
   fun selectAllSortBy_ok() = runTest {
      // arrange
      val expected = _seedPeople.sortedBy { it.lastName.lowercase() }
      // act/arrange
      _dataStore.selectAllSorted().test {
         val actual = awaitItem()
         assertEquals(_seedPeople.size, actual.size)
         assertContentEquals(_seedPeople, actual)
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