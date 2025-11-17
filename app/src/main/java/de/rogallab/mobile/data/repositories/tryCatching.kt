package de.rogallab.mobile.data.repositories

import kotlinx.coroutines.CancellationException

/**
 * Wraps a suspending [block] in a `Result`.
 *
 * Cancellation is rethrown to keep cooperative cancellation intact; every other
 * throwable is converted to `Result.failure`.
 *
 * Use for one-shot repository operations that should surface errors via `Result`
 * instead of throwing.
*/
suspend fun <R> tryCatching(
   block: suspend () -> R,
): Result<R> =
   try {
      Result.success(block())
   }
   catch (e: CancellationException) {
      throw e // not an error
   }
   catch (t: Throwable) {
      Result.failure(t)
   }
