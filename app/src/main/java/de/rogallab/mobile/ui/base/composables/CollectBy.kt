package de.rogallab.mobile.ui.base.composables

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.rogallab.mobile.domain.utilities.logVerbose
import kotlinx.coroutines.flow.StateFlow

//   observe the personUiStateFlow in the ViewModel
//   val lifecycle = (LocalActivity.current as? ComponentActivity)?.lifecycle
//      ?: LocalLifecycleOwner.current.lifecycle
//   val personUiState by viewModel.personUiStateFlow.collectAsStateWithLifecycle(
//      lifecycle = lifecycle,
//      minActiveState = Lifecycle.State.STARTED
//   )
//   LaunchedEffect(personUiState.person) {
//      logDebug(tag, "PersonUiState: ${personUiState.person}")
//   }
@Composable
fun <T> CollectBy (uiStateFlow: StateFlow<T>, tag:String ): T {
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