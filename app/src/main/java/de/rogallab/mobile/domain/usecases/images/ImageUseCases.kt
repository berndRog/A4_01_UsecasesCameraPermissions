package de.rogallab.mobile.domain.usecases.images

import de.rogallab.mobile.domain.IImageUseCases

data class ImageUseCases(
   override val captureImage: ImageUcCaptureCam,
   override val selectImage: ImageUcSelectGal,
   override val deleteImageLocal: ImageUcDeleteLocal
): IImageUseCases


