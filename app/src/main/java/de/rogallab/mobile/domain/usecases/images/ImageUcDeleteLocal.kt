package de.rogallab.mobile.domain.usecases.images

import de.rogallab.mobile.domain.IAppStorage
import java.util.concurrent.CancellationException

class ImageUcDeleteLocal(
   private val _appStorage: IAppStorage
) {
   suspend operator fun invoke(path: String): Result<Unit> =
      try {
         _appStorage.deleteImage(path)
         Result.success(Unit)
      }
      catch (e: Exception) {
         if (e is CancellationException) throw e
         Result.failure(e)
      }
}