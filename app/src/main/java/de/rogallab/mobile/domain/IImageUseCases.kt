package de.rogallab.mobile.domain

import de.rogallab.mobile.domain.usecases.images.ImageUcCaptureCam
import de.rogallab.mobile.domain.usecases.images.ImageUcDeleteLocal
import de.rogallab.mobile.domain.usecases.images.ImageUcSelectGal

interface IImageUseCases {
   val captureImage: ImageUcCaptureCam
   val selectImage: ImageUcSelectGal
   val deleteImageLocal: ImageUcDeleteLocal
}