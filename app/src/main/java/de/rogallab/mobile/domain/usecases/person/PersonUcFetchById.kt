package de.rogallab.mobile.domain.usecases.person

import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person

//class PersonUcFetchById(
//   private val _repository: IPersonRepository
//) {
//   suspend operator fun invoke(id: String): Result<Person> =
//      // isn't possible because Result Type is changed from
//      // Result<Person?> to Result<Person>
//      _repository.findById(id)
//         .onSuccess { person: Person? ->
//            person?.let { Result.success(person) }
//               ?: Result.failure(Exception("Person not found"))
//         }
//         .onFailure { t ->
//            Result.failure(t)
//         }
//}

class PersonUcFetchById(
   private val _repository: IPersonRepository
) {
   suspend operator fun invoke(id: String): Result<Person> =
      _repository.findById(id).fold(
         onSuccess = { person: Person? ->
            // transformation: Result<Person?> to Result<Person>
            person?.let { it: Person -> Result.success(it) }
               ?: Result.failure(Exception("Person not found"))
         },
         onFailure = { t ->
            Result.failure(t)
         }
      )
}
