package de.rogallab.mobile.domain

import de.rogallab.mobile.domain.usecases.person.PersonUcCreate
import de.rogallab.mobile.domain.usecases.person.PersonUcFetchById
import de.rogallab.mobile.domain.usecases.person.PersonUcRemove
import de.rogallab.mobile.domain.usecases.person.PersonUcUpdate

interface IPersonUseCases {
   val fetchById: PersonUcFetchById
   val create: PersonUcCreate
   val update: PersonUcUpdate
   val remove: PersonUcRemove
}