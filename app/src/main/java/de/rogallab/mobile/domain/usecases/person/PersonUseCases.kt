package de.rogallab.mobile.domain.usecases.person

import de.rogallab.mobile.domain.IPersonUseCases

data class PersonUseCases(
   override val fetchById: PersonUcFetchById,
   override val create: PersonUcCreate,
   override val update: PersonUcUpdate,
   override val remove: PersonUcRemove
): IPersonUseCases