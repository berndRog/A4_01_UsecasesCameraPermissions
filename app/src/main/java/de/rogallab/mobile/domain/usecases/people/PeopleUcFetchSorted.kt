package de.rogallab.mobile.domain.usecases.people

import de.rogallab.mobile.domain.IPeopleUcFetchSorted
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person
import kotlinx.coroutines.flow.Flow

class PeopleUcFetchSorted(
   private val _repository: IPersonRepository
): IPeopleUcFetchSorted {
    override suspend operator fun invoke(selector: (Person) -> String?)
    : Flow<Result<List<Person>>> = _repository.getAllSortedBy(selector)
}