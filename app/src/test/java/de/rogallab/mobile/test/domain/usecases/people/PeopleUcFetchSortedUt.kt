package de.rogallab.mobile.test.domain.usecases.people

import app.cash.turbine.test
import de.rogallab.mobile.Globals
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.domain.IPeopleUcFetchSorted
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.IPersonUseCases
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
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// problems with java version 17 and android sdk 36
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PeoplePersonUsecasesUt : KoinTest {

   @get:Rule
   val tempDir = TemporaryFolder()

   @get:Rule
   val mainRule = MainDispatcherRule()

   private lateinit var _uc: IPeopleUcFetchSorted
   private lateinit var _seed: Seed
   private lateinit var _dataStore: IDataStore
   private lateinit var _repository: IPersonRepository
   private lateinit var _appHome: Path
   private lateinit var _filePath: Path
   private lateinit var _seedPeople: List<Person>

   @Before
   fun setup() = runTest {
      // silence logs for tests
      Globals.isInfo = false
      Globals.isDebug = false
      Globals.isVerbose = false
      Globals.isComp = false

      stopKoin() // ensure clean graph per test run

      // Boot Koin graph exactly like your other tests
      val koinApp = startKoin {
         modules(
            defModulesTest(
               appHomePath = tempDir.root.absolutePath,
               ioDispatcher = mainRule.dispatcher()
            )
         )
      }
      val koin = koinApp.koin

      _seed = koin.get<Seed>()
      _dataStore = koin.get<IDataStore>()
      _repository = koin.get<IPersonRepository>()
      _appHome = tempDir.root.toPath()

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
         // cleanup temp directory
         Files.walk(_appHome)
            .sorted(Comparator.reverseOrder())
            .forEach { it.toFile().delete() }
      }
      catch (_: IOException) {
      }
      finally {
         stopKoin()
      }
   }

   @Test
   fun ucFetchSorted_ok() = runTest {
      // arrange (expected sorted by firstName)
      val useCase = get<IPeopleUcFetchSorted>()

      val selector = { p: Person -> p.firstName.lowercase() }
      _seedPeople.sortedBy(selector) // in-place sort of seed data to get expected result
      val expected = _seed.people.toList() // make a copy

      // act + assert
      useCase.invoke().test {
         val result = awaitItem()
         assertTrue(result.isSuccess)
         val actual = result.getOrThrow()

         // same size & same order as expected
         assertEquals(expected.size, actual.size)
         assertContentEquals(expected, actual)
         cancelAndIgnoreRemainingEvents()
      }
   }

   @Test
   fun ucFindById_ok() = runTest {
      // arrange: take a real seeded person id
      val useCases = get<IPersonUseCases>()
      val expected: Person = _seedPeople.first()
      val id = expected.id
      // act/assert
      val result = useCases.fetchById(id)
      assertTrue(result.isSuccess)
      val actual = result.getOrThrow()
      assertEquals(expected, actual)
   }

   @Test
   fun ucCreate_ok() = runTest {
      // arrange: build a new person based on a seed template but new id
      val useCases = get<IPersonUseCases>()
      val newPerson = Person("Bernd","Rogalla", "b-u.rogalla@ostfalia.de", "05862 988 61180")
      // act
      val result = useCases.create(newPerson)
      assertTrue(result.isSuccess)
      // assert: verify persistence through repository
      val resultFindByid = _repository.findById(newPerson.id)
      assertTrue( resultFindByid.isSuccess)
      val actual = resultFindByid.getOrThrow()
      assertNotNull(actual, "Created person should be found")
      assertEquals(newPerson.id, actual!!.id)
      assertEquals(newPerson, actual)
   }
}
