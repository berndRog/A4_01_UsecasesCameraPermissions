package de.rogallab.mobile.data.local.datastore

import android.content.Context
import de.rogallab.mobile.Globals
import de.rogallab.mobile.data.IDataStore
import de.rogallab.mobile.data.local.Seed
import de.rogallab.mobile.domain.entities.Person
import de.rogallab.mobile.domain.utilities.logDebug
import de.rogallab.mobile.domain.utilities.logError
import de.rogallab.mobile.domain.utilities.logVerbose
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DataStore(
   directoryName: String? = null,
   fileName: String? = null,
   private val _context: Context,
   private val _seed: Seed,
   private val _dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IDataStore {

   // directory and file name for the dataStore from MainApplication
   private val _appHome: String = _context.filesDir.toString()
   private var _directoryName = directoryName ?: Globals.directoryName
   private val _fileName = fileName ?: Globals.fileName

   override val filePath: Path = getOrCreateFilePath(
      appHome = _appHome,
      directoryName = _directoryName,
      fileName = _fileName
   )

   // Coroutine-safe locking
   private val mutex = Mutex()

   // Reactive in-memory state
   private val _peopleFlow = MutableStateFlow<List<Person>>(emptyList())
   val peopleFlow: StateFlow<List<Person>> = _peopleFlow.asStateFlow()

   // JSON serializer
   private val _jsonSettings = Json {
      prettyPrint = true
      ignoreUnknownKeys = true
   }

   // Initialize: load from file or seed
   override suspend fun initialize() {
      logDebug(TAG, "init: read datastore")
      mutex.withLock {
         val exists = Files.exists(filePath)
         val size = if (exists) runCatching { Files.size(filePath) }.getOrElse { 0L } else 0L

         if (!exists || size == 0L) {
            _seed?.let { seed ->
               logVerbose(TAG, "create(): seedData ${seed.people.size} people")
               writeInternal(seed.people)
            } ?: run {
               // no seed provided → start with empty file
               writeInternal(emptyList())
            }
         }
         _peopleFlow.value = readInternal()
      }
   }

   // Reactive selects
   override fun selectAll(): Flow<List<Person>> =
      peopleFlow

   override fun selectAllSortedBy(selector: (Person) -> String?): Flow<List<Person>> =
      peopleFlow
         .map { list -> list.sortedBy { selector(it)?.lowercase() } }
         .distinctUntilChanged()

   override fun selectWhere(predicate: (Person) -> Boolean): Flow<List<Person>> =
      peopleFlow
         .map { list -> list.filter(predicate) }
         .distinctUntilChanged()

   // one shot read
   override suspend fun findById(id: String): Person? =
      peopleFlow.value
         .firstOrNull { it.id == id }

   override suspend fun findBy(predicate: (Person) -> Boolean): Person? =
      peopleFlow.value
         .firstOrNull(predicate)

   // one show write
   override suspend fun insert(person: Person) {
      mutex.withLock {
         val current = _peopleFlow.value
         if (current.any { it.id == person.id }) return
         val updated = current + person
         writeInternal(updated)
         _peopleFlow.value = updated
         logVerbose(TAG, "insert: $person")
      }
   }

   override suspend fun update(person: Person) {
      mutex.withLock {
         val current = _peopleFlow.value
         require(current.any { it.id == person.id }) {
            "Person with id ${person.id} does not exist"
         }
         val updated = current.map { if (it.id == person.id) person else it }
         writeInternal(updated)
         _peopleFlow.value = updated
         logVerbose(TAG, "update: $person")
      }
   }

   override suspend fun delete(person: Person) {
      mutex.withLock {
         val current = _peopleFlow.value
         require(current.any { it.id == person.id }) {
            "Person with id ${person.id} does not exist"
         }
         val updated = current.filterNot { it.id == person.id }
         writeInternal(updated)
         _peopleFlow.value = updated
         logVerbose(TAG, "delete: $person")
      }
   }

   // ---------- Internal I/O (always on IO dispatcher) ----------
   private suspend fun readInternal(): List<Person> = withContext(_dispatcher) {
      try {
         val jsonString = try {
            File(filePath.toString()).readText()
         }
         catch (e: IOException) {
            logError(TAG, "Failed to read file: ${e.message}")
            throw e
         }

         if (jsonString.isBlank()) {
            logDebug(TAG, "read(): empty file → 0 people")
            emptyList()
         } else {
            logVerbose(TAG, jsonString)
            val people: List<Person> = _jsonSettings.decodeFromString(jsonString)
            logDebug(TAG, "read(): decode JSON ${people.size} people")
            people
         }
      }
      catch (e: Exception) {
         logError(TAG, "Failed to read: ${e.message}")
         throw e
      }
   }

   private suspend fun writeInternal(people: List<Person>) = withContext(_dispatcher) {
      try {
         val jsonString = _jsonSettings.encodeToString(people)
         logDebug(TAG, "write(): encode JSON ${people.size} people")

         // Ensure directory exists
         val dir = File(filePath.parent.toString())
         if (!dir.exists()) dir.mkdirs()

         // Atomic write via temp file
         val targetFile = File(filePath.toString())
         val tmpFile = File(targetFile.parent, "${targetFile.name}.tmp")
         tmpFile.writeText(jsonString)

         if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
         }
         logVerbose(TAG, jsonString)
      }
      catch (e: Exception) {
         logError(TAG, "Failed to write: ${e.message}")
         throw e
      }
   }

   companion object {
      private const val TAG = "<-DataStore>"

      // Build (and ensure) platform-friendly path like:
      // <UserHome>/Documents/<directoryName>/<fileName>
      fun getOrCreateFilePath(
         appHome: String,
         directoryName: String,
         fileName: String
      ): Path {
         try {
            val dir: Path = Paths.get(appHome)
               .resolve("Documents")
               .resolve(directoryName)
            Files.createDirectories(dir)
            return dir.resolve(fileName)
         }
         catch (e: Exception) {
            logError(TAG, "Failed to create directory or build path: ${e.message}")
            throw e
         }
      }
   }
}