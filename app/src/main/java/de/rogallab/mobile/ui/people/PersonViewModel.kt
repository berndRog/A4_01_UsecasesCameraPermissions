package de.rogallab.mobile.ui.people

import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.viewModelScope
import de.rogallab.mobile.domain.IImageUseCases
import de.rogallab.mobile.domain.IPeopleUseCases
import de.rogallab.mobile.domain.IPersonUseCases
import de.rogallab.mobile.domain.entities.Person
import de.rogallab.mobile.domain.usecases.undoredo.SingleSlotUndoBuffer
import de.rogallab.mobile.domain.usecases.undoredo.optimisticRemove
import de.rogallab.mobile.domain.usecases.undoredo.optimisticUndoRemove
import de.rogallab.mobile.domain.utilities.logDebug
import de.rogallab.mobile.domain.utilities.newUuid
import de.rogallab.mobile.ui.base.BaseViewModel
import de.rogallab.mobile.ui.base.updateState
import de.rogallab.mobile.ui.navigation.INavHandler
import de.rogallab.mobile.ui.navigation.PeopleList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class PersonViewModel(
   private val _peopleUc: IPeopleUseCases,
   private val _personUc: IPersonUseCases,
   private val _imageUc: IImageUseCases,
   private val _navHandler: INavHandler,
   private val _validator: PersonValidator
) : BaseViewModel(_navHandler, TAG) {

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
      }
   }

   init {
      logDebug(TAG, "init instance=${System.identityHashCode(this)}")
      fetch()
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

         is PersonIntent.SelectImage -> selectImage(intent.uriString, intent.groupName)
         is PersonIntent.CaptureImage -> captureImage(intent.uriString, intent.groupName)
         is PersonIntent.CommitDeleteIfNotUndone -> commitDeleteIfNotUndone()

         is PersonIntent.ErrorEvent -> handleErrorEvent(message = intent.message)
         is PersonIntent.UndoEvent -> handleUndoEvent(intent.errorState)
      }
   }
   // endregion

   // region Input updates (immutable copy, trimmed) -----------------------------------------------
   private fun onFirstNameChange(firstName: String) {
      val trimmed = firstName.trim()
      if(trimmed == _personUiStateFlow.value.person.firstName) return
      updateState(_personUiStateFlow) { copy(person = person.copy(firstName = trimmed)) }
   }
   private fun onLastNameChange(lastName: String) {
      val trimmed = lastName.trim()
      if (trimmed == _personUiStateFlow.value.person.lastName) return
      updateState(_personUiStateFlow) { copy(person = person.copy(lastName = lastName.trim())) }
   }
   private fun onEmailChange(email: String?) {
      var trimmed = email?.trim()
      if (trimmed == _personUiStateFlow.value.person.email) return
      updateState(_personUiStateFlow) { copy(person = person.copy(email = email?.trim())) }
   }
   private fun onPhoneChange(phone: String?) {
      val trimmed = phone?.trim()
      if (trimmed == _personUiStateFlow.value.person.phone) return
      updateState(_personUiStateFlow) { copy(person = person.copy(phone = trimmed)) }
   }
   private fun onImagePathChange(uriString: String?) {
      val trimmed = uriString?.trim()
      if (trimmed == _personUiStateFlow.value.person.imagePath) return
      updateState(_personUiStateFlow) { copy(person = person.copy(imagePath = trimmed)) }
   }

   // clear person state and prepare for new person input
   private fun clearState() =
      updateState(_personUiStateFlow) { copy(person = Person(id = newUuid())) }
   // endregion


   // region Fetch by id (error → navigate back to list) -------------------------------------------
   private fun fetchById(id: String) {
      logDebug(TAG, "fetchById() $id")
      viewModelScope.launch {
         _personUc.fetchById(id)
            .onSuccess { person ->
               updateState(_personUiStateFlow) { copy(person = person) }
            }
            .onFailure { handleErrorEvent(it, navKey = PeopleList) }
      }
   }
   // endregion

   // region Create/Update (persist then refresh list) --------------------------
   private fun create() {
      logDebug(TAG, "create")
      viewModelScope.launch {
         _personUc.create(_personUiStateFlow.value.person)
            .onSuccess {  }
            .onFailure { handleErrorEvent(it) }
      }
   }
   private fun update() {
      logDebug(TAG, "update()")
      viewModelScope.launch {
         _personUc.updateWithLocalImage(_personUiStateFlow.value.person)
            .onSuccess {  }
            .onFailure { handleErrorEvent(it) }
      }
   }
   private fun remove(person: Person) {
      logDebug(TAG, "remove()")
      viewModelScope.launch {
         _personUc.remove(person)
            .onSuccess { }
            .onFailure { handleErrorEvent(it) }
      }
   }
   // endregion

   // region Single-slot UNDO buffer ---------------------------------------------------------------
   // Single-slot undo buffer for the last removed Person.
   private var _undoBuffer = SingleSlotUndoBuffer<Person>()

   // Removes a person using the "Optimistic-then-Persist" pattern.
   // Optimistic:
   //  - The UI list is updated immediately so the UI feels instant.
   //  - The removed item and index are stored in the undo buffer.
   // Then-Persist:
   // - The actual repository deletion happens in the background.
   // - If persistence fails, an error event is emitted.
   private fun removeUndo(person: Person) {
      logDebug(TAG, "removeUndo(${person.id})")

      val currentList = _peopleUiStateFlow.value.people

      // 1) Pure helper: perform optimistic list change and build undo buffer
      val (updatedList, newBuffer) = optimisticRemove(
         list = currentList,
         item = person,
         getId = { it.id },
         undoBuffer = _undoBuffer
      )
      // If nothing changed (item not found), stop here
      if (updatedList === currentList) return

      // 2) Update undo buffer
      _undoBuffer = newBuffer

      // 3) Immediately update UI state (Optimistic)
      updateState(_peopleUiStateFlow) { copy(people = updatedList) }

      // 4) Persist removal asynchronously (Then-Persist)
      viewModelScope.launch {
         logDebug(TAG, "persistRemove(${person.id})")
         _personUc.remove(person)
            .onFailure { handleErrorEvent(it) }
      }
   }

   // Undoes the last removed person using the pure helper.
   // Optimistic:
   //  - The item is reinserted immediately into the UI list.
   //  - The undo buffer is cleared.
   // Then-Persist:
   // - The Person is recreated in the repository in the background.
   // If the Person was already present in the list (rare edge case),
   // only the buffer is cleared and no repository call is made.
   private fun undoRemove() {
      logDebug(TAG, "undoRemove()")

      val currentList = _peopleUiStateFlow.value.people

      // 1) Pure helper: restore from undo buffer
      val result = optimisticUndoRemove(
         list = currentList,
         getId = { it.id },
         undoBuffer = _undoBuffer
      )
      // Extract results
      val updatedList = result.updatedList
      val restoredId = result.restoredId

      // 2) Clear or update undo buffer
      _undoBuffer = result.newBuffer
      // If nothing changed, stop here
      if (updatedList === currentList && restoredId == null) return

      // 3) Update UI state immediately (Optimistic)
      updateState(_peopleUiStateFlow) {
         copy(people = updatedList, restoredPersonId = restoredId) }

      // 4) Persist recreation in the background (Then-Persist)
      if (restoredId != null) {
         val restoredPerson = updatedList.firstOrNull { it.id == restoredId }
         if (restoredPerson != null) {
            viewModelScope.launch {
               logDebug(TAG, "persistCreate(${restoredPerson.id})")
               _personUc.create(restoredPerson)
                  .onFailure { handleErrorEvent(it) }
            }
         }
      }
   }
   // Called by the UI once the scroll animation to restoredPersonId has finished.
   // This clears the flag so future restore operations can update it again.
   private fun restored() {
      logDebug(TAG, "restored() acknowledged by UI")
      updateState(_peopleUiStateFlow) { copy(restoredPersonId = null) }
   }

   // Finalizes a person deletion **after** the UNDO window has expired.
   // Is triggered from the Snackbar's `onDismissed` callback.
   // It is called ONLY when the user did *not* press the UNDO action.
   // Workflow:
   // - Read the last removed person from the undo buffer.
   //    If the buffer is empty → the user has already undone the removal → nothing to do.
   // - Extract the `imagePath` of the removed person.
   // - Immediately clear the undo buffer so that the deletion is final and cannot be undone anymore.
   // - If the person had a local image, delete it from app storage.
   // This completes the two-phase deletion workflow:
   private fun commitDeleteIfNotUndone() {
      logDebug(TAG, "commitDeleteIfNotUndone()")

      val removed = _undoBuffer.removedItem ?: return
      val imagePath = removed.imagePath

      // Clear buffer first so we don't accidentally reuse it
      _undoBuffer = _undoBuffer.cleared()

      if (imagePath.isNullOrBlank()) return

      viewModelScope.launch {
         _imageUc.deleteImageLocal(imagePath)
            .onFailure { handleErrorEvent(it) }
      }
   }
   // endregion

   // region Image selection -----------------------------------------------------------------------
   private fun selectImage(uriString: String, groupName: String) {
      viewModelScope.launch {
         _imageUc.selectImage(uriString, groupName).fold(
            onSuccess = { uri ->
               val path = uri.path ?: uri.toString()
               handlePersonIntent(PersonIntent.ImagePathChange(path))
            },
            onFailure = { handleErrorEvent(it) }
         )
      }
   }
   private fun captureImage(uriString: String, groupName: String) {
      viewModelScope.launch {
         _imageUc.captureImage(uriString, groupName).fold(
            onSuccess = { uri ->
               val path = uri.path ?: uri.toString()
               handlePersonIntent(PersonIntent.ImagePathChange(path))
            },
            onFailure = {  handleErrorEvent(it) }
         )
      }
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
   // Read all people from the repository and keep PeopleUiState in sync
   // with the reactive backed flow.
   private var _fetchJob: Job? = null

   private fun fetch() {
      // stop a running flow connection
      _fetchJob?.cancel()

      _fetchJob = viewModelScope.launch {
         _peopleUc.fetchSorted()
            // Runs once when the flow collection starts
            // (e.g. in init{} or when fetch() is explicitly called again).
            .onStart {
               updateState(_peopleUiStateFlow) { copy(isLoading = true) }
            }
            // Handle exceptions coming from the upstream flow
            .catch { t ->
               updateState(_peopleUiStateFlow) { copy(isLoading = false) }
               handleErrorEvent(t)
            }
            // Collect the Result<List<Person>> emitted by the Room-backed flow.
            // Room will emit again whenever the underlying table changes.
            .collectLatest { result ->
               result
                  .onSuccess { people ->
                     logDebug(TAG,
                        "fetch() -> onSuccess: set isLoading = false, people size = ${people.size}")
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


   fun cleanUp() {
      logDebug(TAG, "cleanUp()")
      updateState(_peopleUiStateFlow) { copy(isLoading = false) }
   }
   // endregion

   companion object {
      private const val TAG = "<-PersonViewModel"
   }
}