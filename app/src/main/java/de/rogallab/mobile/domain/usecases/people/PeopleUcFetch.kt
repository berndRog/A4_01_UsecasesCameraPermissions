package de.rogallab.mobile.domain.usecases.people

import de.rogallab.mobile.domain.IPeopleUcFetch
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person

class PeopleUcFetch(
   private val _repository: IPersonRepository
): IPeopleUcFetch {
    override operator fun invoke(selector: (Person) -> String?): Result<List<Person>> =
       _repository.getAllSortedBy(selector)
}