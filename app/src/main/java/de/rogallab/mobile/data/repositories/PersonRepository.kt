package de.rogallab.mobile.data.repositories

import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.domain.IPersonRepository
import de.rogallab.mobile.domain.entities.Person
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

class PersonRepository(
   private val _dataStore: IDataStore,
   private val _dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IPersonRepository {
   override fun getAllSorted(): Flow<Result<List<Person>>> =
//    _dataStore.selectAllSorted().asResult()
      _dataStore.selectAllSorted()
         // Flow<List<Person>> -> Flow<Result<List<Person>>>
         .map { value -> Result.success(value) }
         .catch { e ->
            // Cancellation is not an error. Let it propagate.
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(Result.failure(e))
         }
         .flowOn(_dispatcher)

   override suspend fun findById(id: String): Result<Person?> =
//    tryCatching { _dataStore.findById(id)  }
      try {
         Result.success( _dataStore.findById(id) )
      }
      catch (e: CancellationException) { throw e }
      catch (e: Exception) { Result.failure(e) }

   override suspend fun create(person: Person): Result<Unit> =
      tryCatching { _dataStore.insert(person) }

   override suspend fun update(person: Person): Result<Unit> =
      tryCatching { _dataStore.update(person) }

   override suspend fun remove(person: Person): Result<Unit> =
      tryCatching { _dataStore.delete(person) }

}