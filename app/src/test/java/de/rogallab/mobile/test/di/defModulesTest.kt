package de.rogallab.mobile.test.di

import android.content.Context
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.data.local.appstorage.AppStorage
import de.rogallab.mobile.data.local.datastore.DataStore
import de.rogallab.mobile.data.repositories.PersonRepository
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IPeopleUcFetchSorted
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.IPersonUseCases
import de.rogallab.mobile.domain.usecases.people.PeopleUcFetchSorted
import de.rogallab.mobile.domain.usecases.person.PersonUcCreate
import de.rogallab.mobile.domain.usecases.person.PersonUcFetchById
import de.rogallab.mobile.domain.usecases.person.PersonUcRemove
import de.rogallab.mobile.domain.usecases.person.PersonUcUpdate
import de.rogallab.mobile.domain.usecases.person.PersonUseCases
import de.rogallab.mobile.domain.utilities.logInfo
import de.rogallab.mobile.domain.utilities.newUuid
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.Nav3ViewModel
import de.rogallab.mobile.ui.navigation.PeopleList
import de.rogallab.mobile.ui.people.PersonValidator
import de.rogallab.mobile.ui.people.PersonViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module


fun defModulesTest(
   appHomePath: String,
   ioDispatcher: CoroutineDispatcher
): Module = module {
   val tag = "<-defModulesTest"

   // data modules
   single<CoroutineDispatcher>(named("dispatcherIo")) {
      ioDispatcher
   }

   logInfo(tag, "test single    -> ApplicationProvider.getApplicationContext()")
   single<Context> {
      ApplicationProvider.getApplicationContext()
   }

   logInfo(tag, "test single    -> Seed")
   single<Seed> {
      Seed(
         _context = get<Context>(),
         _isTest = false
      )
   }

   // use factory to get a new instance each time (to avoid data conflicts in tests)
   logInfo(tag, "test single    -> DataStore: DataStore")
   single<IDataStore> {
      DataStore(
         directoryName = "androidTest",
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

   logInfo(tag, "test single    -> PersonRepository: IPersonRepository")
   single<IPersonRepository> {
      PersonRepository(
         _dataStore = get<IDataStore>()  // dependency injection of DataStore
      )
   }

   // domain modules
   // UseCases
   logInfo(tag, "single    -> PeopleUcFetch")
   single<IPeopleUcFetchSorted> {
      PeopleUcFetchSorted(get<IPersonRepository>())
   }

   // single PersonUseCases
   logInfo(tag, "single    -> PersonUcFetchById")
   single { PersonUcFetchById(get<IPersonRepository>()) }
   logInfo(tag, "single    -> PersonUcCreate")
   single { PersonUcCreate(get<IPersonRepository>()) }
   logInfo(tag, "single    -> PersonUcUpdate")
   single { PersonUcUpdate(get<IPersonRepository>()) }
   logInfo(tag, "single    -> PersonUcRemove")
   single { PersonUcRemove(get<IPersonRepository>()) }
   // Aggregation
   logInfo(tag, "single    -> PersonUseCasesc: IPersonUseCases")
   single<IPersonUseCases> {
      PersonUseCases(
         fetchById = get<PersonUcFetchById>(),
         create = get<PersonUcCreate>(),
         update = get<PersonUcUpdate>(),
         remove = get<PersonUcRemove>()
      )
   }


   // ui modules
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
         _fetchSorted = get<IPeopleUcFetchSorted>(),
         _personUc = get<IPersonUseCases>(),
         navHandler = navHandler,
         _validator = get<PersonValidator>()
      )
   }
}