package de.rogallab.mobile.domain.usecases.person

import de.rogallab.mobile.domain.IAppStorage
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person
import kotlin.getOrElse
import kotlin.onSuccess

/**
 * Update an existing Person, including a possibly new imagePath.
 *
 * Workflow:
 *  1) Load the currently persisted Person to compare current.imagePath with person.imagePath.
 *  2) Update the Person in the repository.
 *  3) If the imagePath changed, delete the old image file from app storage.
 *
 * This keeps the app storage clean and avoids accumulating orphaned images.
 */
class PersonUcUpdateWithLocalImage(
   private val _repository: IPersonRepository,
   private val _appStorage: IAppStorage
) {
   suspend operator fun invoke(person: Person): Result<Unit> {

      // Load current person to compare image paths
      val current = _repository.findById(person.id)
         .getOrElse { return Result.failure(it) }
         ?: return Result.failure(kotlin.IllegalArgumentException("Person not found"))

      // Update person with Room
      return _repository.update(person)
         .onSuccess {
            // CleanUp old image if any
            if (current.imagePath != null && current.imagePath != person.imagePath) {
               _appStorage.deleteImage(current.imagePath)
            }
         }
   }
}
