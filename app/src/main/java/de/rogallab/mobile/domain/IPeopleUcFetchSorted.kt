package de.rogallab.mobile.domain

import de.rogallab.mobile.domain.entities.Person

interface IPeopleUcFetchSorted {
   operator fun invoke(selector: (Person) -> String?): Result<List<Person>>
}


