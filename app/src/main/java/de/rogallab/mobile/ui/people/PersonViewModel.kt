package de.rogallab.mobile.ui.people

import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.rogallab.mobile.domain.IPeopleUcFetchSorted
import de.rogallab.mobile.domain.IPersonUseCases
import de.rogallab.mobile.domain.entities.Person
import de.rogallab.mobile.domain.utilities.logDebug
import de.rogallab.mobile.domain.utilities.newUuid
import de.rogallab.mobile.ui.base.BaseViewModel
import de.rogallab.mobile.ui.base.updateState
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.PeopleList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.onSuccess

class PersonViewModel(
   private val _fetchSorted: IPeopleUcFetchSorted,
   private val _personUc: IPersonUseCases,
   navHandler: INavHandler,
   private val _validator: PersonValidator
) : BaseViewModel(navHandler, TAG) {

   init {
      logDebug(TAG, "init instance=${System.identityHashCode(this)}")
   }

   // region StateFlows and Intent handlers --------------------------------------------------------
   // StateFlow for PeopleListScreen ---------------------------------------------------------------
   private val _peopleUiStateFlow: MutableStateFlow<PeopleUiState> =
      MutableStateFlow(PeopleUiState())
   val peopleUiStateFlow: StateFlow<PeopleUiState> =
      _peopleUiStateFlow.asStateFlow()

   // Transform PeopleIntent into an action
   fun handlePeopleIntent(intent: PeopleIntent) {
      when (intent) {
         is PeopleIntent.Fetch -> fetch()
         is PeopleIntent.Clean -> cleanUp()
      }
   }

   // StateFlow for PersonInput-/ PersonDetailScreen -----------------------------------------------
   private val _personUiStateFlow: MutableStateFlow<PersonUiState> =
      MutableStateFlow(PersonUiState())
   val personUiStateFlow: StateFlow<PersonUiState> =
      _personUiStateFlow.asStateFlow()

   // Transform PersonIntent into an action --------------------------------------------------------
   fun handlePersonIntent(intent: PersonIntent) {
      when (intent) {
         is PersonIntent.FirstNameChange -> onFirstNameChange(intent.firstName)
         is PersonIntent.LastNameChange -> onLastNameChange(intent.lastName)
         is PersonIntent.EmailChange -> onEmailChange(intent.email)
         is PersonIntent.PhoneChange -> onPhoneChange(intent.phone)
         is PersonIntent.ImagePathChange -> onImagePathChange(intent.uriString)

         is PersonIntent.Clear -> clearState()
         is PersonIntent.FetchById -> fetchById(intent.id)
         is PersonIntent.Create -> create()
         is PersonIntent.Update -> update()
         is PersonIntent.Remove -> remove(intent.person)

         is PersonIntent.RemoveUndo -> removeUndo(intent.person)
         is PersonIntent.Undo -> undoRemove()
         is PersonIntent.Restored -> restored()

         is PersonIntent.ErrorEvent -> handleErrorEvent(message = intent.message)
         is PersonIntent.UndoEvent -> handleUndoEvent(intent.errorState)
      }
   }
   // endregion

   // region Input updates (immutable copy, trimmed) -----------------------------------------------
   private fun onFirstNameChange(firstName: String) =
      updateState(_personUiStateFlow) {
         copy(person = person.copy(firstName = firstName.trim()))
      }

   private fun onLastNameChange(lastName: String) =
      updateState(_personUiStateFlow) {
         copy(person = person.copy(lastName = lastName.trim()))
      }

   private fun onEmailChange(email: String?) =
      updateState(_personUiStateFlow) {
         copy(person = person.copy(email = email?.trim()))
      }

   private fun onPhoneChange(phone: String?) =
      updateState(_personUiStateFlow) {
         copy(person = person.copy(phone = phone))
      }

   private fun onImagePathChange(uriString: String?) =
      updateState(_personUiStateFlow) {
         copy(person = person.copy(imagePath = uriString?.trim()))
      }

   // clear person state and prepare for new person input
   private fun clearState() =
      updateState(_personUiStateFlow) {
         copy(person = Person(id = newUuid()))
      }
   // endregion

   // region Fetch by id (error → navigate back to list) -------------------------------------------
   private fun fetchById(id: String) {
      logDebug(TAG, "fetchById() $id")
      viewModelScope.launch {
         _personUc.fetchById(id)
            .onSuccess { person ->
               logDebug(TAG, "fetchPersonById")
               updateState(_personUiStateFlow) { copy(person = person) }
            }
            .onFailure { t ->
               handleErrorEvent(t, navKey = PeopleList)
            }
      }
   }
   // endregion

   // region Create/Update (persist then refresh list) --------------------------
   private fun create() {
      logDebug(TAG, "create")
      viewModelScope.launch {
         _personUc.create(_personUiStateFlow.value.person)
            .onSuccess {  } // dot reread all people, is a hot flow now
            .onFailure { handleErrorEvent(it) }
      }
   }
   private fun update() {
      logDebug(TAG, "update()")
      viewModelScope.launch {
         _personUc.update(_personUiStateFlow.value.person)
            .onSuccess {  } // dot reread all people, is a hot flow now
            .onFailure { handleErrorEvent(it) }
      }
   }
   private fun remove(person: Person) {
      logDebug(TAG, "remove()")
      viewModelScope.launch {
         _personUc.remove(_personUiStateFlow.value.person)
            .onSuccess { } // dot reread all people, is a hot flow now
            .onFailure { handleErrorEvent(it) }
      }
   }
   // endregion

   // region Single-slot UNDO buffer ---------------------------------------------------------------
   private var _removedPerson: Person? = null
   private var _removedPersonIndex: Int = -1 // Store only the index

   private fun removeUndo(person: Person) {
      logDebug(TAG, "removePerson()")
      removeItem(
         item = person,
         currentList = _peopleUiStateFlow.value.people,
         getId = { it.id },
         onRemovedItem = { _removedPerson = it as? Person },
         onRemovedItemIndex = { _removedPersonIndex = it },
         updateUi = { updatedList -> updateState(_peopleUiStateFlow) { copy(people = updatedList) } },
         persistRemove = { _personUc.remove(it) },
         tag = TAG
      )
   }

   private fun undoRemove() {
      undoItem(
         currentList = _peopleUiStateFlow.value.people,
         getId = { it.id },
         removedItem = _removedPerson,
         removedIndex = _removedPersonIndex,
         updateUi = { restoredList, restoredId ->
            updateState(_peopleUiStateFlow) { copy(people = restoredList, restoredPersonId = restoredId) }
         },
         persistCreate = { _personUc.create(it) },
         onReset = { _removedPerson = null; _removedPersonIndex = -1 },
         tag = TAG
      )
   }

   private fun restored() {
      logDebug(TAG, "restored() acknowledged by UI")
      // The UI has finished scrolling, so we clear the ID
      updateState(_peopleUiStateFlow) { copy(restoredPersonId = null) }
   }
   // endregion

   // region Validation ----------------------------------------------------------------------------
   // validate all input fields after user finished input into the form
   fun validate(): Boolean {
      val person = _personUiStateFlow.value.person
      // only one error message can be processed at a time
      if (!validateAndLogError(_validator.validateFirstName(person.firstName)))
         return false
      if (!validateAndLogError(_validator.validateLastName(person.lastName)))
         return false
      if (!validateAndLogError(_validator.validateEmail(person.email)))
         return false
      if (!validateAndLogError(_validator.validatePhone(person.phone)))
         return false
      return true // all fields are valid
   }

   private fun validateAndLogError(validationResult: Pair<Boolean, String>): Boolean {
      val (error, message) = validationResult
      if (error) {
         handleErrorEvent(
            message = message,
            withDismissAction = true,
            onDismissed = { /* no op, Unit returned */ },
            duration = SnackbarDuration.Long,
            navKey = null, // stay on the screen
         )
         return false
      }
      return true
   }
   // endregion

   // region Fetch all (persisted → UI) ------------------------------------------------------------
   // read all people from repository
   private fun fetch() {
      viewModelScope.launch {
         _fetchSorted { it.firstName }
            // consume isLoading state
            .onStart {
               updateState(_peopleUiStateFlow) { copy(isLoading = true) }
            }
            .catch { t ->
               updateState(_peopleUiStateFlow) { copy(isLoading = false) }
               handleErrorEvent(t)
            }
            .collectLatest { result ->
               result
                  // consume people list
                  .onSuccess { people ->
                     val snapshot = people.toList()
                     updateState(_peopleUiStateFlow) { copy(isLoading = false, people = snapshot) }
                  }
                  .onFailure { t ->
                     updateState(_peopleUiStateFlow) { copy(isLoading = false) }
                     handleErrorEvent(t)
                  }
            }
      } // end launch
   }

   private fun cleanUp() {
      logDebug(TAG, "cleanUp()")
      updateState(_peopleUiStateFlow) { copy(isLoading = false) }
   }
   // endregion

   companion object {
      private const val TAG = "<-PersonViewModel"
   }
}