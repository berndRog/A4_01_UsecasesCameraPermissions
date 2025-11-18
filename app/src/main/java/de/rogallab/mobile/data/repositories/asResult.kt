package de.rogallab.mobile.data.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Converts a Flow<T> into Flow<Result<T>> while handling cancellation correctly.
 *
 * - Normal values are wrapped as Result.success(...)
 * - Exceptions are converted to Result.failure(...)
 * - CancellationException is *not* treated as an error and is re-thrown.
 *
 * This ensures that coroutine cancellation (e.g., when leaving a screen
 * or when using collectLatest) does not trigger UI error handling.
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> =
   this // :Flow<T>
      .map { value -> Result.success(value) }
      .catch { e ->
         // Very important: Cancellation is not an error. Let it propagate.
         if (e is CancellationException) throw e
         emit(Result.failure(e))
      }

