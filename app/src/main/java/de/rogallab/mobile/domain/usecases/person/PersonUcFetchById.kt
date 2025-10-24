package de.rogallab.mobile.domain.usecases.person

import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person

class PersonUcFetchById(
   private val _repository: IPersonRepository
) {
   operator fun invoke(id: String): Result<Person> =
      _repository.findById(id).fold(
         onSuccess = { person ->
            person?.let { Result.success(person) }
               ?: Result.failure(Exception("Person not found"))
         },
         onFailure = { t ->
            Result.failure(t)
         }
      )
}
