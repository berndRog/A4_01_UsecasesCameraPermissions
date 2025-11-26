package de.rogallab.mobile.domain.usecases.people

import de.rogallab.mobile.domain.IPeopleUseCases

data class PeopleUseCases(
   override val fetchSorted: PeopleUcFetchSorted,
): IPeopleUseCases