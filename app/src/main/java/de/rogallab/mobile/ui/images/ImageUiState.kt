package de.rogallab.mobile.ui.images

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class ImageUiState(
   val imageUri: Uri? = null
)