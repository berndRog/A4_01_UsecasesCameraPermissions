package de.rogallab.mobile.ui.people.composables

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.rogallab.mobile.Globals
import de.rogallab.mobile.R
import de.rogallab.mobile.domain.utilities.logComp
import de.rogallab.mobile.domain.utilities.logVerbose
import de.rogallab.mobile.ui.errors.ErrorHandler
import de.rogallab.mobile.ui.features.people.composables.PersonContent
import de.rogallab.mobile.ui.images.ImageViewModel
import de.rogallab.mobile.ui.people.PersonIntent
import de.rogallab.mobile.ui.people.PersonValidator
import de.rogallab.mobile.ui.people.PersonViewModel
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.koinInject

@Composable
fun <T> collectBy (uiStateFlow: StateFlow<T>, tag:String ): T {
   val lifecycle = (LocalActivity.current as? ComponentActivity)?.lifecycle
      ?: LocalLifecycleOwner.current.lifecycle
   val uiState: T by uiStateFlow.collectAsStateWithLifecycle(
      lifecycle = lifecycle,
      minActiveState = Lifecycle.State.STARTED
   )
   LaunchedEffect(uiState) {
      logVerbose(tag, "uiState: ${uiState.toString()}")
   }
   return uiState
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
   id: String,
   viewModel: PersonViewModel,
   imageViewModel: ImageViewModel,
   onNavigateReverse: () -> Unit = {},
) {
   val tag = "<-PersonDetailScreen"
   val nComp = remember { mutableIntStateOf(1) }
   SideEffect { logComp(tag, "Composition #${nComp.value++}") }

   val groupName = Globals.FILE_NAME.split(".").first()

   // observe the personUiStateFlow in the ViewModel
//   val lifecycle = (LocalActivity.current as? ComponentActivity)?.lifecycle
//      ?: LocalLifecycleOwner.current.lifecycle
//   val personUiState by viewModel.personUiStateFlow.collectAsStateWithLifecycle(
//      lifecycle = lifecycle,
//      minActiveState = Lifecycle.State.STARTED
//   )
//   LaunchedEffect(personUiState.person) {
//      logDebug(tag, "PersonUiState: ${personUiState.person}")
//   }
   val personUiState = collectBy(viewModel.personUiStateFlow, tag)


   // fetch person by id
   LaunchedEffect(id) {
      viewModel.handlePersonIntent(PersonIntent.FetchById(id))
   }

   val snackbarHostState = remember { SnackbarHostState() }

   Scaffold(
      contentColor = MaterialTheme.colorScheme.onBackground,
      contentWindowInsets = WindowInsets.safeDrawing, // .safeContent .safeGestures,
      topBar = {
         TopAppBar(
            title = { Text(text = stringResource(R.string.personDetail)) },
            navigationIcon = {
               IconButton(onClick = {
                  if (viewModel.validate()) {
                     viewModel.handlePersonIntent(PersonIntent.Update)
                     onNavigateReverse()
                  }
               }) {
                  Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                     contentDescription = stringResource(R.string.back))
               }
            }
         )
      },
      snackbarHost = {
         SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(
               snackbarData = data,
               actionOnNewLine = true
            )
         }
      },
      modifier = Modifier.fillMaxSize()
   ) { innerPadding ->

      Column(
         modifier = Modifier
            .padding(paddingValues = innerPadding)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
      ) {
         PersonContent(
            personUiState = personUiState,
            validator = koinInject<PersonValidator>(),
            //imageLoader = koinInject(),
            onFirstNameChange = {
               viewModel.handlePersonIntent(PersonIntent.FirstNameChange(it))
            },
            onLastNameChange = {
               viewModel.handlePersonIntent(PersonIntent.LastNameChange(it))
            },
            onEmailChange = {
               viewModel.handlePersonIntent(PersonIntent.EmailChange(it))
            },
            onPhoneChange = {
               viewModel.handlePersonIntent(PersonIntent.PhoneChange(it))
            },
            onSelectImage = {
               imageViewModel.selectImage(it, groupName) { uriString ->
                  viewModel.handlePersonIntent(PersonIntent.ImagePathChange(uriString))
               }
            },
            onCaptureImage = {
               imageViewModel.captureImage(it) { uriString ->
                  viewModel.handlePersonIntent(PersonIntent.ImagePathChange(uriString))
               }
            },
            handleError = { message ->
               message?.let {
                  viewModel.handlePersonIntent(PersonIntent.ErrorEvent(it))
               }
            },
         )
      }
   }

   // Error handling
   ErrorHandler(
      viewModel = viewModel,
      snackbarHostState = snackbarHostState
   )
}