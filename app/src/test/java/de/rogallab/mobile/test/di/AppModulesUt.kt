package de.rogallab.mobile.test.di

import androidx.test.core.app.ApplicationProvider
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.di.defModules
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IImageUseCases
import de.rogallab.mobile.domain.IAppMediaStore
import de.rogallab.mobile.domain.IPeopleUseCases
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.IPersonUseCases
import de.rogallab.mobile.domain.usecases.images.ImageUcCaptureCam
import de.rogallab.mobile.domain.usecases.images.ImageUcSelectGal
import de.rogallab.mobile.domain.usecases.people.PeopleUcFetchSorted
import de.rogallab.mobile.domain.usecases.person.PersonUcCreate
import de.rogallab.mobile.domain.usecases.person.PersonUcFetchById
import de.rogallab.mobile.domain.usecases.person.PersonUcRemove
import de.rogallab.mobile.domain.usecases.person.PersonUcUpdateWithLocalImage
import de.rogallab.mobile.test.TestApplication
import de.rogallab.mobile.ui.people.PersonValidator
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AppModulesTest.kt
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class AppModulesUt : KoinTest {

   @get:Rule
   val koinTestRule = KoinTestRule.create {
      androidContext(ApplicationProvider.getApplicationContext<TestApplication>())
      modules(defModules)
   }

   // Schnittstellen / Klassen, die wir testen wollen
   private val dispatcherIo: CoroutineDispatcher by inject(named("DispatcherIo"))
   private val dispatcherDefault: CoroutineDispatcher by inject(named("DispatcherDefault"))

   private val seed: Seed by inject()

   private val appStorage: IAppStorage by inject()
   private val dataStore: IDataStore by inject()
   private val mediaStore: IAppMediaStore by inject()

   private val personRepository: IPersonRepository by inject()

   private val peopleUcFetchSorted: PeopleUcFetchSorted by inject()
   private val peopleUseCases: IPeopleUseCases by inject()

   private val personUcFetchById: PersonUcFetchById by inject()
   private val personUcCreate: PersonUcCreate by inject()
   private val personUcUpdate: PersonUcUpdateWithLocalImage by inject()
   private val personUcRemove: PersonUcRemove by inject()
   private val personUseCases: IPersonUseCases by inject()

   private val imageUcCaptureCam: ImageUcCaptureCam by inject()
   private val imageUcSelectGal: ImageUcSelectGal by inject()
   private val imageUseCases: IImageUseCases by inject()

   private val personValidator: PersonValidator by inject()

   // --- einzelne Tests ---

   @Test
   fun dispatcherIo_isProvided() {
      assertNotNull(dispatcherIo)
   }

   @Test
   fun dispatcherDefault_isProvided() {
      assertNotNull(dispatcherDefault)
   }

   @Test
   fun seed_isProvided() {
      assertNotNull(seed)
   }

   @Test
   fun appStorage_isProvided() {
      assertNotNull(appStorage)
   }

   @Test
   fun dataStore_isProvided() {
      assertNotNull(dataStore)
   }

   @Test
   fun mediaStore_isProvided() {
      assertNotNull(mediaStore)
   }

   @Test
   fun personRepository_isProvided() {
      assertNotNull(personRepository)
   }

   @Test
   fun peopleUcFetchSorted_isProvided() {
      assertNotNull(peopleUcFetchSorted)
   }

   @Test
   fun peopleUseCases_isProvided() {
      assertNotNull(peopleUseCases)
   }

   @Test
   fun personUseCases_singleUseCases_areProvided() {
      assertNotNull(personUcFetchById)
      assertNotNull(personUcCreate)
      assertNotNull(personUcUpdate)
      assertNotNull(personUcRemove)
   }

   @Test
   fun personUseCases_aggregated_isProvided() {
      assertNotNull(personUseCases)
   }

   @Test
   fun imageUseCases_singleUseCases_areProvided() {
      assertNotNull(imageUcCaptureCam)
      assertNotNull(imageUcSelectGal)
   }

   @Test
   fun imageUseCases_aggregated_isProvided() {
      assertNotNull(imageUseCases)
   }

   @Test
   fun personValidator_isProvided() {
      assertNotNull(personValidator)
   }
}