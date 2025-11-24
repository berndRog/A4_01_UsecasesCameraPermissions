package de.rogallab.mobile.data

import de.rogallab.mobile.domain.entities.Person
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

interface IDataStore {
   val filePath: Path

   fun selectAllSorted(): Flow<List<Person>>

   suspend fun findById(id: String): Person?

   suspend fun insert(person: Person)
   suspend fun update(person: Person)
   suspend fun delete(person: Person)

   suspend fun initialize()
}