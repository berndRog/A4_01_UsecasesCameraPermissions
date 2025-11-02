package de.rogallab.mobile.domain.usecases.person

import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person

class PersonUcCreate(
   private val _repository: IPersonRepository
) {
    suspend operator fun invoke(person: Person): Result<Unit> =
        _repository.create(person)
}