// androidTest/java/de/rogallab/mobile/ui/people/PersonInputScreenTest.kt

package de.rogallab.mobile.androidTest.ui.people

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.rogallab.mobile.R
import de.rogallab.mobile.ui.images.ImageViewModel
import de.rogallab.mobile.ui.people.PersonIntent
import de.rogallab.mobile.ui.people.PersonUiState
import de.rogallab.mobile.ui.people.PersonViewModel
import de.rogallab.mobile.ui.people.composables.PersonInputScreen
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonInputScreenTest {

   @get:Rule
   val composeRule = createAndroidComposeRule<ComponentActivity>()

   private lateinit var personViewModel: PersonViewModel
   private lateinit var imageViewModel: ImageViewModel

   @Before
   fun setUp() {
      MockKAnnotations.init(this, relaxUnitFun = true)

      // PersonViewModel als Mock
      personViewModel = mockk(relaxed = true)

      // PersonUiStateFlow stubben
      every { personViewModel.personUiStateFlow } returns
         MutableStateFlow(PersonUiState()) // ggf. Default-Konstruktor anpassen

      // ImageViewModel brauchen wir nur als Dummy
      imageViewModel = mockk(relaxed = true)
   }

   @Test
   fun topBarTitle_isShown() {
      composeRule.setContent {
         PersonInputScreen(
            viewModel = personViewModel,
            imageViewModel = imageViewModel,
         )
      }

      val title = composeRule.activity.getString(R.string.personInput)

      composeRule.onNodeWithText(title)
         .assertExists()
   }

   @Test
   fun backClick_whenValidateTrue_callsCreate_andNavigatesBack() {
      // Arrange
      every { personViewModel.validate() } returns true
      var navigatedBack = false

      composeRule.setContent {
         PersonInputScreen(
            viewModel = personViewModel,
            imageViewModel = imageViewModel,
            onNavigateReverse = { navigatedBack = true }
         )
      }

      val backDescription = composeRule.activity.getString(R.string.back)

      // Act
      composeRule.onNodeWithContentDescription(backDescription)
         .performClick()

      // Assert
      verify(exactly = 1) { personViewModel.validate() }
      verify(exactly = 1) { personViewModel.handlePersonIntent(PersonIntent.Create) }
      assertTrue("onNavigateReverse should be called when validate() returns true", navigatedBack)
   }

   @Test
   fun backClick_whenValidateFalse_doesNotCreate_andDoesNotNavigateBack() {
      // Arrange
      every { personViewModel.validate() } returns false
      var navigatedBack = false

      composeRule.setContent {
         PersonInputScreen(
            viewModel = personViewModel,
            imageViewModel = imageViewModel,
            onNavigateReverse = { navigatedBack = true }
         )
      }

      val backDescription = composeRule.activity.getString(R.string.back)

      // Act
      composeRule.onNodeWithContentDescription(backDescription)
         .performClick()

      // Assert
      verify(exactly = 1) { personViewModel.validate() }
      verify(exactly = 0) { personViewModel.handlePersonIntent(PersonIntent.Create) }
      assertFalse("onNavigateReverse must NOT be called when validate() returns false", navigatedBack)
   }
}
