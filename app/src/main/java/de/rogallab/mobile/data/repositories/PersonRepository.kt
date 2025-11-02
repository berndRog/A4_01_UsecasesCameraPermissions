package de.rogallab.mobile.data.repositories

import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.text.insert

class PersonRepository(
   private val _dataStore: IDataStore
) : IPersonRepository {
   override fun getAll(): Flow<Result<List<Person>>> =
      _dataStore.selectAll()
         .map { Result.success(it) }
         .catch { e -> emit(Result.failure(e)) }

   override fun getAllSortedBy(selector: (Person) -> String?): Flow<Result<List<Person>>> =
      _dataStore.selectAllSortedBy(selector)
         .map { Result.success(it) }
         .catch { e -> emit(Result.failure(e)) }

   override fun getWhere(predicate: (Person) -> Boolean): Flow<Result<List<Person>>> =
      _dataStore.selectWhere(predicate)
         .map { Result.success(it) }
         .catch { e -> emit(Result.failure(e)) }

   override suspend fun findById(id: String): Result<Person?> =
      runCatching { _dataStore.findById(id)  }

   override suspend fun findBy(predicate: (Person) -> Boolean): Result<Person?> =
      runCatching { _dataStore.findBy(predicate)  }

   override suspend fun create(person: Person): Result<Unit> =
      runCatching { _dataStore.insert(person) }

   override suspend fun update(person: Person): Result<Unit> =
      runCatching { _dataStore.update(person) }

   override suspend fun remove(person: Person): Result<Unit> =
      runCatching { _dataStore.delete(person) }

}