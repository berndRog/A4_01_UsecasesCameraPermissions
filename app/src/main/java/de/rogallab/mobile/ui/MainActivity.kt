package de.rogallab.mobile.ui

//import de.rogallab.mobile.ui.navigation.composables.AppNavHost
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import de.rogallab.mobile.domain.utilities.logComp
import de.rogallab.mobile.domain.utilities.logVerbose
import de.rogallab.mobile.ui.base.BaseActivity
import de.rogallab.mobile.ui.navigation.composables.AppNavigation
import de.rogallab.mobile.ui.permissions.buildPermissionReport
import de.rogallab.mobile.ui.theme.AppTheme

class MainActivity : BaseActivity(TAG) {

   // lazy initialization of the ViewModel with koin
   // Activity-scoped ViewModels viewModelStoreOwner = MainActivity
//   private val _navViewModel: Nav3ViewModel by viewModel {
//      parametersOf(PeopleList) }
//
//   private val _personViewModel: PersonViewModel by viewModel{
//      parametersOf(_navViewModel as INavHandler) }
//   private val _imageViewModel: ImageViewModel by viewModel{
//      parametersOf(_navViewModel as INavHandler) }


   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)

//      logDebug(TAG, "_navViewModel=${System.identityHashCode(_navViewModel)}")
//      logDebug(TAG, "_peopleViewModel=${System.identityHashCode(_personViewModel)}")

      val permissionReport = this.buildPermissionReport()
      permissionReport.forEach { logVerbose(TAG,it.toFormattedString()) }

      enableEdgeToEdge()

      setContent {

         val nComp = remember { mutableIntStateOf(1) }
         SideEffect { logComp(TAG, "Composition #${nComp.intValue++}") }

         AppTheme {
            AppNavigation()
         }
      }

   }

   companion object {
      private const val TAG = "<-MainActivity"
   }
}
