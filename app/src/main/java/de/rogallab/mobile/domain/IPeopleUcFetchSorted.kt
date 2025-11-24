package de.rogallab.mobile.domain

import de.rogallab.mobile.domain.entities.Person
import kotlinx.coroutines.flow.Flow

interface IPeopleUcFetchSorted {
   suspend operator fun invoke(): Flow<Result<List<Person>>>
}


