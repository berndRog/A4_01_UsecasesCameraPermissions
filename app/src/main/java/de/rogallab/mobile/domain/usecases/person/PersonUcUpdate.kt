package de.rogallab.mobile.domain.usecases.person

import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person

class PersonUcUpdate(
   private val _repository: IPersonRepository
) {
    operator fun invoke(person: Person): Result<Unit> =
       _repository.update(person)
}