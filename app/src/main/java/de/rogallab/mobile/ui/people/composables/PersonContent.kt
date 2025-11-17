package de.rogallab.mobile.ui.people.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import de.rogallab.mobile.R
import de.rogallab.mobile.domain.utilities.logComp
import de.rogallab.mobile.ui.base.composables.InputValueString
import de.rogallab.mobile.ui.images.composables.SelectAndShowImage
import de.rogallab.mobile.ui.people.PersonUiState
import de.rogallab.mobile.ui.people.PersonValidator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonContent(
   personUiState: PersonUiState,
   validator: PersonValidator,
   onFirstNameChange: (String) -> Unit,
   onLastNameChange: (String) -> Unit,
   onEmailChange: (String) -> Unit,
   onPhoneChange: (String) -> Unit,
   onSelectImage: (String) -> Unit,
   onCaptureImage: (String) -> Unit,
   handleError: (String?) -> Unit,
) {
   val tag = "<-PersonContent"
   val nComp = remember { mutableIntStateOf(1) }
   SideEffect { logComp(tag, "Composition #${nComp.value++}") }

   Column {
      InputValueString(
         value = personUiState.person.firstName,
         onValueChange = onFirstNameChange,
         label = stringResource(R.string.firstName),
         validate = validator::validateFirstName,
         ascii = false,
         leadingIcon = Icons.Outlined.Person,
         keyboardType = KeyboardType.Text,
         imeAction = ImeAction.Next,

      )
      InputValueString(
         value = personUiState.person.lastName,
         onValueChange = onLastNameChange,
         label = stringResource(R.string.lastName),
         validate = validator::validateLastName,
         ascii = false,
         leadingIcon = Icons.Outlined.Person,
         keyboardType = KeyboardType.Text,
         imeAction = ImeAction.Next,
      )
      InputValueString(
         value = personUiState.person.email ?: "",
         onValueChange = onEmailChange,
         label = stringResource(R.string.email),
         validate = validator::validateEmail,
         ascii = true, // sanitize email input äöüß -> aeoeuss
         leadingIcon = Icons.Outlined.Email,
         keyboardType = KeyboardType.Email,
         imeAction = ImeAction.Next,

      )
      InputValueString(
         value = personUiState.person.phone ?: "",
         onValueChange = onPhoneChange,
         label = stringResource(R.string.phone),
         validate = validator::validatePhone,
         ascii = false,
         leadingIcon = Icons.Outlined.Phone,
         keyboardType = KeyboardType.Phone,
         imeAction = ImeAction.Done,
      )

      SelectAndShowImage(
         localImage = personUiState.person.imagePath,
   //    imageLoader = koinInject(),
         onSelectImage = onSelectImage,
         onCaptureImage = onCaptureImage,
         handleError = handleError
      )
   }
}
