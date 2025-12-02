package de.rogallab.mobile.ui.navigation.composables

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import de.rogallab.mobile.Globals
import de.rogallab.mobile.domain.utilities.logComp
import de.rogallab.mobile.domain.utilities.logDebug
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.Nav3ViewModel
import de.rogallab.mobile.ui.navigation.PeopleList
import de.rogallab.mobile.ui.navigation.PersonDetail
import de.rogallab.mobile.ui.navigation.PersonInput
import de.rogallab.mobile.ui.people.PersonViewModel
import de.rogallab.mobile.ui.people.composables.PeopleListScreen
import de.rogallab.mobile.ui.people.composables.PersonDetailScreen
import de.rogallab.mobile.ui.people.composables.PersonInputScreen
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AppNavigation(
   // navViewModel is Activity-scoped
   navViewModel: Nav3ViewModel = koinActivityViewModel { parametersOf(PeopleList) },
   personViewModel: PersonViewModel = koinActivityViewModel{ parametersOf(navViewModel) },
   animationDuration: Int = Globals.animationDuration
) {
   val tag = "<-AppNavigation"
   val nComp = remember { mutableIntStateOf(1) }
   SideEffect { logComp(tag, "Composition #${nComp.intValue++}") }

   // Use the navViewModel's backStack to manage navigation state
   val backStack = navViewModel.backStack

   NavDisplay(
      backStack = backStack,
      onBack = {
         logDebug(tag, "onBack() - Backstack size: ${backStack.size}")
         navViewModel.pop()
      },
      entryDecorators = listOf(
         // Save/restore per-entry UI state
         rememberSaveableStateHolderNavEntryDecorator(
            rememberSaveableStateHolder()
         ),
         // Enables entry-scoped ViewModels if you also want them somewhere else
         rememberViewModelStoreNavEntryDecorator()
      ),
      // Standard Android navigation animations:
      // transitionSpec:    New screen slides in from the right ({ it }),
      //                    old slides out to the left ({ -it }).
      // popTransitionSpec: New screen slides in from the left ({ -it }),
      //                    old slides out to the right ({ it }).
      transitionSpec = {
         slideInHorizontally(
            animationSpec = tween(animationDuration)
         ){ it } togetherWith
         slideOutHorizontally(
            animationSpec = tween(animationDuration)
         ){ -it }
      },
      popTransitionSpec = {
         slideInHorizontally(
            animationSpec = tween(animationDuration)
         ){ -it } togetherWith
            slideOutHorizontally(
               animationSpec = tween(animationDuration)
            ){ it }
      },
      //
      predictivePopTransitionSpec = {
         slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Up,
            animationSpec = tween(animationDuration)
         ) togetherWith
            fadeOut(animationSpec = tween(animationDuration*3/2 ))
      },

      entryProvider = entryProvider {

         entry<PeopleList> { _ ->
            PeopleListScreen(
               viewModel = personViewModel,
               onNavigatePersonInput = {
                  navViewModel.push(PersonInput)
               },
               onNavigatePersonDetail = { personId ->
                  navViewModel.push(PersonDetail(personId))
               }
            )
         }
         entry<PersonInput> { _ ->
            PersonInputScreen(
               viewModel = personViewModel,
               onNavigateReverse =  navViewModel::pop
            )
         }
         entry<PersonDetail> { key ->
            PersonDetailScreen(
               id = key.id,
               viewModel = personViewModel,
               onNavigateReverse = navViewModel::pop
            )
         }
      },
   )
}