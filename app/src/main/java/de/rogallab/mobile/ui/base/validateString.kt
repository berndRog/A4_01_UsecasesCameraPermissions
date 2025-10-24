package de.rogallab.mobile.ui.base/*
   Generic string validator
   param value String to validate
   param min minimum length, default 2
   param max maximum length, default 64
   param messageToShort, default "message too short"
   param messageToLong, default "message too long"
   return Pair<Boolean, String>  isError:Boolean, message:String
 */
fun validateString(
   value: String,
   min:Int = 2,
   max: Int = 64,
   messageToShort: String = "value too short",
   messageToLong: String = "value too long"
): Pair<Boolean, String> =
   if (value.length < min)
      Pair(true, messageToShort)
   else if (value.length > max)
      Pair(true, messageToLong)
   else
      Pair(false, "")