package de.rogallab.mobile.ui.images

import androidx.lifecycle.viewModelScope
import de.rogallab.mobile.domain.IImageUseCases
import de.rogallab.mobile.ui.base.BaseViewModel
import de.rogallab.mobile.ui.navigation.INavHandler
import kotlinx.coroutines.launch

class ImageViewModel(
   private val _imageUc: IImageUseCases,
   navHandler: INavHandler,
) : BaseViewModel(navHandler, TAG) {

   // StateFlow not need, we are using the stateflow of PersonUiState
   // i.e. via update of person.imagePath to save it
   // private val _imageUiStateFlow = MutableStateFlow(ImageUiState())
   // val imagesUiStateFlow: StateFlow<ImageUiState> = _imageUiStateFlow.asStateFlow()

   // Direct functions with return values
   fun selectImage(
      uriString: String,
      groupName: String,
      onResult: (String?) -> Unit // Event to update imagePath in person
   ): Unit {
      viewModelScope.launch {
         _imageUc.selectImage(uriString, groupName).fold(
            onSuccess = { uri ->
               // updateState(_imageUiStateFlow) { copy(imageUri = uri) }
               // update imagePath instead
               onResult(uri.toString())
            },
            onFailure = { t ->
               handleErrorEvent(t)
               onResult(null)
            }
         )
      }
   }

   fun captureImage(
      uriString: String,
      groupName: String,
      onResult: (String?) -> Unit // Event to update imagePath in person
   ){
      viewModelScope.launch {
         _imageUc.captureImage(uriString, groupName).fold(
            onSuccess = { uri ->
               // updateState(_imageUiStateFlow) { copy(imageUri = uri) }
               // update imagePath instead
               onResult(uri.toString())
            },
            onFailure = { t ->
               handleErrorEvent(t)
               onResult(null)
            }
         )
      }
   }

   fun clearCapturedUri() {
//      _imageUiStateFlow.update { currentState ->
//         currentState.copy(capturedImageUri = null)
//      }
   }

   companion object {
      private const val TAG = "ImageViewModel"
   }
}