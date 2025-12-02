package de.rogallab.mobile.androidTest.di

import android.content.Context
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.data.local.appstorage.AppStorage
import de.rogallab.mobile.data.local.datastore.DataStore
import de.rogallab.mobile.data.local.mediastore.AppMediaStore
import de.rogallab.mobile.data.repositories.PersonRepository
import de.rogallab.mobile.domain.IAppMediaStore
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IImageUseCases
import de.rogallab.mobile.domain.IPeopleUseCases
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.IPersonUseCases
import de.rogallab.mobile.domain.usecases.people.PeopleUcFetchSorted
import de.rogallab.mobile.domain.usecases.people.PeopleUseCases
import de.rogallab.mobile.domain.usecases.person.PersonUcCreate
import de.rogallab.mobile.domain.usecases.person.PersonUcFetchById
import de.rogallab.mobile.domain.usecases.person.PersonUcRemove
import de.rogallab.mobile.domain.usecases.person.PersonUcUpdateWithLocalImage
import de.rogallab.mobile.domain.usecases.person.PersonUseCases
import de.rogallab.mobile.domain.utilities.logInfo
import de.rogallab.mobile.domain.utilities.newUuid
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.Nav3ViewModel
import de.rogallab.mobile.ui.navigation.PeopleList
import de.rogallab.mobile.ui.people.PersonValidator
import de.rogallab.mobile.ui.people.PersonViewModel
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun defModulesAndroidTest(
   appHomeName: String,
   directoryName: String,
   fileName: String,
   ioDispatcher: CoroutineDispatcher
): Module = module {
   val tag = "<-defModulesTest"

   // data modules
   single<CoroutineDispatcher>(named("dispatcherIo")) {
      ioDispatcher
   }

   //== data modules ===============================================================================
   logInfo(tag, "test single    -> ApplicationProvider.getApplicationContext()")
   single<Context> {
      ApplicationProvider.getApplicationContext()
   }

   logInfo(tag, "test single    -> Seed")
   single<Seed> {
      Seed(
         _context = get<Context>(),
         _isTest = true
      )
   }

   // use factory to get a new instance each time (to avoid data conflicts in tests)
   logInfo(tag, "test single    -> DataStore: DataStore")
   single<IDataStore> {
      DataStore(
         appHomeName = appHomeName,
         directoryName = directoryName,
         fileName = "testPeople_${newUuid()}",
         _context = get<Context>(),
         _seed = get<Seed>(),
         _dispatcher = get(named("dispatcherIo")),
      )
   }

   logInfo(tag, "test single    -> AppStorage: IAppStorage")
   single<IAppStorage> {
      AppStorage(
         _context = get<Context>(),
         _dispatcher = get(named("dispatcherIo")),
      )
   }

   logInfo(tag, "test single    -> MediaStore: IMediaStore")
   single<IAppMediaStore> {
      AppMediaStore(
         _context = get<Context>(),
         _dispatcher = get(named("dispatcherIo")),
      )
   }

   logInfo(tag, "test single    -> PersonRepository: IPersonRepository")
   single<IPersonRepository> {
      PersonRepository(
         _dataStore = get<IDataStore>()  // dependency injection of DataStore
      )
   }

//== domain modules =============================================================================
   // PeopleUseCases
   logInfo(tag, "single    -> PeopleUcFetchSorted")
   single<PeopleUcFetchSorted> {
      PeopleUcFetchSorted(get<IPersonRepository>())
   }
   // Aggregation
   logInfo(tag, "single    -> PeopleUseCases: IPeopleUseCases")
   single<IPeopleUseCases> {
      PeopleUseCases(
         fetchSorted = get<PeopleUcFetchSorted>()
      )
   }

   // PersonUseCases
   // single PersonUseCases
   logInfo(tag, "single    -> PersonUcFetchById")
   single { PersonUcFetchById(get<IPersonRepository>()) }
   logInfo(tag, "single    -> PersonUcCreate")
   single { PersonUcCreate(get<IPersonRepository>()) }
   logInfo(tag, "single    -> PersonUcUpdateWithLocalImage")
   single {
      PersonUcUpdateWithLocalImage(
         _repository = get<IPersonRepository>(),
         _appStorage = get<IAppStorage>()
      )
   }

   logInfo(tag, "single    -> PersonUcRemove")
   single { PersonUcRemove(get<IPersonRepository>()) }
   // Aggregation
   logInfo(tag, "single    -> PersonUseCasesc: IPersonUseCases")
   single<IPersonUseCases> {
      PersonUseCases(
         fetchById = get<PersonUcFetchById>(),
         create = get<PersonUcCreate>(),
         updateWithLocalImage = get<PersonUcUpdateWithLocalImage>(),
         remove = get<PersonUcRemove>()
      )
   }

   //== ui modules =================================================================================
   logInfo(tag, "test single    -> PersonValidator")
   single<PersonValidator> {
      PersonValidator(
         _context = get<Context>()
      )
   }

   single<INavHandler> {
      Nav3ViewModel(startDestination = PeopleList)
   }

   logInfo(tag, "test viewModel -> Nav3ViewModel as INavHandler (with params)")
   factory { (startDestination: NavKey) ->  // Parameter for startDestination
      Nav3ViewModel(startDestination = startDestination)
   } bind INavHandler::class

   logInfo(tag, "viewModel -> PersonViewModel")
   factory { (navHandler: INavHandler) ->
      PersonViewModel(
         _peopleUc = get<IPeopleUseCases>(),
         _personUc = get<IPersonUseCases>(),
         _imageUc = get<IImageUseCases>(),
         _navHandler = navHandler,
         _validator = get<PersonValidator>()
      )
   }
}