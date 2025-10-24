package de.rogallab.mobile.domain.usecases.people

import de.rogallab.mobile.domain.IPeopleUcFetchSorted
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person

class PeopleUcFetchSorted(
   private val _repository: IPersonRepository
): IPeopleUcFetchSorted {
    override operator fun invoke(selector: (Person) -> String?): Result<List<Person>> =
       _repository.getAllSortedBy(selector)
}