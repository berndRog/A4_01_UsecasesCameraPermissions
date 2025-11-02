package de.rogallab.mobile.domain

import de.rogallab.mobile.domain.entities.Person
import kotlinx.coroutines.flow.Flow

interface IPersonRepository {
   fun getAll():                                         Flow<Result<List<Person>>>
   fun getAllSortedBy(selector: (Person) -> String?):    Flow<Result<List<Person>>>
   fun getWhere(predicate: (Person) -> Boolean):         Flow<Result<List<Person>>>
   suspend fun findById(id: String):                     Result<Person?>
   suspend fun findBy(predicate: (Person) -> Boolean):   Result<Person?>

   suspend fun create(person: Person): Result<Unit>
   suspend fun update(person: Person): Result<Unit>
   suspend fun remove(person: Person): Result<Unit>
}