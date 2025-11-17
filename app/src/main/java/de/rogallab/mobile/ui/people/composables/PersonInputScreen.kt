package de.rogallab.mobile.ui.people.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.rogallab.mobile.Globals
import de.rogallab.mobile.R
import de.rogallab.mobile.domain.utilities.logComp
import de.rogallab.mobile.ui.base.composables.CollectBy
import de.rogallab.mobile.ui.errors.ErrorHandler
import de.rogallab.mobile.ui.images.ImageViewModel
import de.rogallab.mobile.ui.people.PeopleIntent
import de.rogallab.mobile.ui.people.PersonIntent
import de.rogallab.mobile.ui.people.PersonUiState
import de.rogallab.mobile.ui.people.PersonValidator
import de.rogallab.mobile.ui.people.PersonViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonInputScreen(
   viewModel: PersonViewModel,
   imageViewModel: ImageViewModel,
   onNavigateReverse: () -> Unit = {},
) {
   val tag = "<-PersonInputScreen"
   val nComp = remember { mutableIntStateOf(1) }
   SideEffect { logComp(tag, "Composition #${nComp.value++}") }

   val groupName = Globals.fileName.split(".").first()

   // observe PersonUiStateFlow
   val personUiState: PersonUiState = CollectBy(viewModel.personUiStateFlow, tag)

   val snackbarHostState = remember { SnackbarHostState() }
   Scaffold(
      contentColor = MaterialTheme.colorScheme.onBackground,
      contentWindowInsets = WindowInsets.safeDrawing, // .safeContent .safeGestures,
      topBar = {
         TopAppBar(
            title = { Text(text = stringResource(R.string.personInput)) },
            navigationIcon = {
               IconButton(
                  onClick = {
                     if(viewModel.validate()) {
                        viewModel.handlePersonIntent(PersonIntent.Create)
                        onNavigateReverse()
                     }
                  }
               ) {
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
            onSelectImage = { imageViewModel.selectImage(it, groupName) { uriString ->
               viewModel.handlePersonIntent(PersonIntent.ImagePathChange(uriString)) }
            },
            onCaptureImage = { imageViewModel.captureImage(it, groupName) { uriString ->
               viewModel.handlePersonIntent(PersonIntent.ImagePathChange(uriString)) }
            },
            handleError = { message -> message?.let {
                  viewModel.handlePersonIntent(PersonIntent.ErrorEvent(it)) }
            },
         )
      }
   }

   ErrorHandler(
      viewModel = viewModel,
      snackbarHostState = snackbarHostState,
      onCleanUp = { viewModel.handlePeopleIntent(PeopleIntent.Clean)}
   )
}