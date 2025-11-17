package de.rogallab.mobile.di

import androidx.navigation3.runtime.NavKey
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.data.local.appstorage.AppStorage
import de.rogallab.mobile.data.local.datastore.DataStore
import de.rogallab.mobile.data.local.mediastore.MediaStore
import de.rogallab.mobile.data.repositories.PersonRepository
import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IImageUseCases
import de.rogallab.mobile.domain.IMediaStore
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.IPersonUseCases
import de.rogallab.mobile.domain.usecases.images.ImageUcCaptureCam
import de.rogallab.mobile.domain.usecases.images.ImageUcSelectGal
import de.rogallab.mobile.domain.usecases.images.ImageUseCases
import de.rogallab.mobile.domain.usecases.people.PeopleUcFetchSorted
import de.rogallab.mobile.domain.usecases.person.PersonUcCreate
import de.rogallab.mobile.domain.usecases.person.PersonUcFetchById
import de.rogallab.mobile.domain.usecases.person.PersonUcRemove
import de.rogallab.mobile.domain.usecases.person.PersonUcUpdate
import de.rogallab.mobile.domain.usecases.person.PersonUseCases
import de.rogallab.mobile.domain.utilities.logInfo
import de.rogallab.mobile.ui.images.ImageViewModel
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.Nav3ViewModel
import de.rogallab.mobile.ui.navigation.PeopleList
import de.rogallab.mobile.ui.people.PersonValidator
import de.rogallab.mobile.ui.people.PersonViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel      // für viewModel { ... }
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val defModules: Module = module {
   val tag = "<-defModules"

   // Provide Dispatchers
   logInfo(tag, "single    -> MainDispatcher:CoroutineDispatcher")
   single<CoroutineDispatcher>(named("MainDispatcher")) { Dispatchers.Main }
   logInfo(tag, "single    -> IODispatcher:CoroutineDispatcher")
   single<CoroutineDispatcher>(named("IODispatcher")) { Dispatchers.IO }
   logInfo(tag, "single    -> DefaultDispatcher:CoroutineDispatcher")
   single<CoroutineDispatcher>(named("DefaultDispatcher")) { Dispatchers.Default }

   logInfo(tag, "single    -> Seed")
   single<Seed> {
      Seed(
         _context = androidContext(),
         _isTest = false
      )
   }

   // data modules
   logInfo(tag, "single    -> AppStorage:IAppStorage")
   single<IAppStorage> {
      AppStorage(
         _context = androidContext(),
         _dispatcher = get<CoroutineDispatcher>(named("IODispatcher"))
      )
   }


   logInfo(tag, "single    -> DataStore")
   single<IDataStore> {
      DataStore(
         directoryName = null,
         fileName = null,
         _context = androidContext(),
         _seed = get<Seed>()
      )
   }

   logInfo(tag, "single    -> MediaStore:IMediaStore")
   single<IMediaStore> {
      MediaStore(
         _context = androidContext(),
         _ioDispatcher = get<CoroutineDispatcher>(named("IODispatcher"))
      )
   }

   logInfo(tag, "single    -> PersonRepository: IPersonRepository")
   single<IPersonRepository> {
      PersonRepository(
         _dataStore = get<IDataStore>()
      )
   }

   // domain modules
   // UseCases
   logInfo(tag, "single    -> PeopleUcFetch")
   single<PeopleUcFetchSorted> {
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

   // single ImageUseCases
   logInfo(tag, "single    -> ImagesUcCapture")
   single {
      ImageUcCaptureCam(
         _appStorage = get<IAppStorage>(),
         _mediaStore = get<IMediaStore>()
      )
   }
   logInfo(tag, "single    -> ImageUcSelectFromGallery")
   single {
      ImageUcSelectGal(
         _appStorage = get<IAppStorage>(),
         _mediaStore = get<IMediaStore>()
      )
   }

   // Aggregation
   single<IImageUseCases> {
      ImageUseCases(
         captureImage = get<ImageUcCaptureCam>(),
         selectImage = get<ImageUcSelectGal>(),
      )
   }

   // ui modules
   logInfo(tag, "single    -> PersonValidator")
   single {
      PersonValidator(androidContext())
   }

   logInfo(tag, "viewModel -> Nav3ViewModel as INavHandler (with params)")
   viewModel { (startDestination: NavKey) ->  // Parameter for startDestination
      Nav3ViewModel(startDestination = startDestination)
   } bind INavHandler::class

   logInfo(tag, "viewModel -> ImageViewModel")
   viewModel { (navHandler: INavHandler) ->
      ImageViewModel(
         _imageUc = get<IImageUseCases>(),
         navHandler = navHandler,
      )
   }

   logInfo(tag, "viewModel -> PersonViewModel")
   viewModel { (navHandler: INavHandler) ->
      PersonViewModel(
         _fetchSorted = get<PeopleUcFetchSorted>(),
         _personUc = get<IPersonUseCases>(),
         // _repository = get<IPersonRepository>(),
         navHandler = navHandler,
         _validator = get<PersonValidator>()
         //_ioDispatcher = get<CoroutineDispatcher>(named("IODispatcher"))
      )
   }
}

val appModules: Module = module {

   try {
      val testedModules = defModules
      requireNotNull(testedModules) {
         "definedModules is null"
      }
      includes(
         testedModules,
         //useCaseModules
      )
   }
   catch (e: Exception) {
      logInfo("<-appModules", e.message!!)
   }
}
