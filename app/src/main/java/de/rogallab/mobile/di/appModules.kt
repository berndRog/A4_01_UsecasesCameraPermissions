package de.rogallab.mobile.di

import androidx.navigation3.runtime.NavKey
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
import de.rogallab.mobile.domain.usecases.images.ImageUcCaptureCam
import de.rogallab.mobile.domain.usecases.images.ImageUcDeleteLocal
import de.rogallab.mobile.domain.usecases.images.ImageUcSelectGal
import de.rogallab.mobile.domain.usecases.images.ImageUseCases
import de.rogallab.mobile.domain.usecases.people.PeopleUcFetchSorted
import de.rogallab.mobile.domain.usecases.people.PeopleUseCases
import de.rogallab.mobile.domain.usecases.person.PersonUcCreate
import de.rogallab.mobile.domain.usecases.person.PersonUcFetchById
import de.rogallab.mobile.domain.usecases.person.PersonUcRemove
import de.rogallab.mobile.domain.usecases.person.PersonUcUpdateWithLocalImage
import de.rogallab.mobile.domain.usecases.person.PersonUseCases
import de.rogallab.mobile.domain.utilities.logInfo
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.Nav3ViewModel
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

val defModules: Module = module {
   val tag = "<-defModules"

   // Provide Dispatchers
   logInfo(tag, "single    -> DispatcherIo:CoroutineDispatcher")
   single<CoroutineDispatcher>(named("DispatcherIo")) { Dispatchers.IO }
   logInfo(tag, "single    -> DispatcherDefault:CoroutineDispatcher")
   single<CoroutineDispatcher>(named("DispatcherDefault")) { Dispatchers.Default }

   //== data modules ===============================================================================
   logInfo(tag, "single    -> Seed")
   single<Seed> {
      Seed(
         _context = androidContext(),
         _isTest = false
      )
   }

   logInfo(tag, "single    -> DataStore: IDataStore")
   single<IDataStore> {
      DataStore(
         appHomeName = null,
         directoryName = null,
         fileName = null,
         _context = androidContext(),
         _seed = get<Seed>(),
         _dispatcher = get<CoroutineDispatcher>(named("DispatcherIo"))
      )
   }

   logInfo(tag, "single    -> AppStorage:IAppStorage")
   single<IAppStorage> {
      AppStorage(
         _context = androidContext(),
         _dispatcher = get<CoroutineDispatcher>(named("DispatcherIo"))
      )
   }

   logInfo(tag, "single    -> AppMediaStore:IAppMediaStore")
   single<IAppMediaStore> {
      AppMediaStore(
         _context = androidContext(),
         _dispatcher = get<CoroutineDispatcher>(named("DispatcherIo"))
      )
   }

   logInfo(tag, "single    -> PersonRepository: IPersonRepository")
   single<IPersonRepository> {
      PersonRepository(
         _dataStore = get<IDataStore>()
      )
   }

   //== domain modules =============================================================================
   // People UseCases
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

   // single PersonUseCases
   logInfo(tag, "single    -> PersonUcFetchById")
   single { PersonUcFetchById(get<IPersonRepository>()) }
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

   // single ImageUseCases
   logInfo(tag, "single    -> ImagesUcCapture")
   single {
      ImageUcCaptureCam(
         _appStorage = get<IAppStorage>(),
         _mediaStore = get<IAppMediaStore>()
      )
   }
   logInfo(tag, "single    -> ImageUcSelectFromGallery")
   single {
      ImageUcSelectGal(
         _appStorage = get<IAppStorage>(),
         _mediaStore = get<IAppMediaStore>()
      )
   }
   logInfo(tag, "single    -> ImageUcDeleteLocalSelectFromGallery")
   single {
      ImageUcSelectGal(
         _appStorage = get<IAppStorage>(),
         _mediaStore = get<IAppMediaStore>()
      )
   }
   logInfo(tag, "single    -> ImageUcDeleteLocal")
   single {
      ImageUcDeleteLocal(
         _appStorage = get<IAppStorage>()
      )
   }
   // Aggregation
   single<IImageUseCases> {
      ImageUseCases(
         captureImage = get<ImageUcCaptureCam>(),
         selectImage = get<ImageUcSelectGal>(),
         deleteImageLocal = get<ImageUcDeleteLocal>()
      )
   }

   //== ui modules =================================================================================
   logInfo(tag, "single    -> PersonValidator")
   single {
      PersonValidator(androidContext())
   }

   logInfo(tag, "viewModel -> Nav3ViewModel as INavHandler (with params)")
   viewModel { (startDestination: NavKey) ->  // Parameter for startDestination
      Nav3ViewModel(startDestination = startDestination)
   } bind INavHandler::class

   logInfo(tag, "viewModel -> PersonViewModel")
   viewModel { (navHandler: INavHandler) ->
      PersonViewModel(
         _peopleUc = get<IPeopleUseCases>(),
         _personUc = get<IPersonUseCases>(),
         _imageUc = get<IImageUseCases>(),
         _navHandler = navHandler,
         _validator = get<PersonValidator>()
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
