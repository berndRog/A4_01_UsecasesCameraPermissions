package de.rogallab.mobile.ui.navigation.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

@Composable
fun rememberPeopleGraphOwner(key: String = "people_graph"): ViewModelStoreOwner {
   // Create a dedicated ViewModelStore for the "People" feature graph
   val store = remember(key) { ViewModelStore() }

   // Clear the ViewModelStore when this owner leaves composition
   DisposableEffect(key) {
      onDispose { store.clear() }
   }

   // Return a ViewModelStoreOwner that exposes the store property
   return remember(key) {
      object : ViewModelStoreOwner {
         override val viewModelStore: ViewModelStore = store
      }
   }
}



