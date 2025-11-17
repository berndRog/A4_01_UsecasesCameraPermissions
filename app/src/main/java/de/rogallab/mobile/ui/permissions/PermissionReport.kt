package de.rogallab.mobile.ui.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.core.content.ContextCompat

/**
 * Builds a report of all permissions declared in the app manifest.
 *
 * For each requested permission, this function determines:
 *  - the permission's protection level (normal / dangerous / signature / special)
 *  - whether the permission requires a runtime request (dangerous)
 *  - whether the permission is currently granted or denied
 *
 * Works on all Android versions. Uses legacy protectionLevel flags, therefore
 * @Suppress("DEPRECATION") is placed at function level intentionally.
 */
@Suppress("DEPRECATION")
fun Context.buildPermissionReport(): List<PermissionReport> {
   val pm = packageManager
   val pkg = packageName

   // 1) Retrieve manifest-declared permissions of this app
   val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
      pm.getPackageInfo(
         pkg,
         PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
      )
   } else {
      @Suppress("DEPRECATION")
      pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
   }

   val requestedPermissions: List<String> =
      pkgInfo.requestedPermissions?.toList().orEmpty()

   // 2) Build a report entry for each declared permission
   return requestedPermissions.map { permissionName ->

      // 2a) Query the platform's PermissionInfo for this permission
      val pInfo = try {
         pm.getPermissionInfo(permissionName, 0)
      } catch (_: Exception) {
         null // permission may no longer exist or be private
      }

      // 2b) Determine protection level and runtime requirement
      val (protectionLabel, requiresRuntime) =
         if (pInfo == null) {
            "unknown" to false
         } else {
            val baseProtection =
               pInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE

            when (baseProtection) {
               PermissionInfo.PROTECTION_NORMAL ->
                  "normal" to false

               PermissionInfo.PROTECTION_DANGEROUS ->
                  "dangerous" to true   // requires runtime request

               PermissionInfo.PROTECTION_SIGNATURE ->
                  "signature/special" to false

               else ->
                  "signature/special" to false // includes privileged / appop / oem / vendor
            }
         }

      // 3) Check whether permission is currently granted
      val isGranted =
         ContextCompat.checkSelfPermission(this, permissionName) ==
            PackageManager.PERMISSION_GRANTED

      PermissionReport(
         name = permissionName,
         protection = protectionLabel,
         granted = isGranted,
         needsRuntimeRequest = requiresRuntime
      )
   }
}

/**
 * Data class representing the permission analysis result.
 */
data class PermissionReport(
   val name: String,
   val protection: String,          // "normal" | "dangerous" | "signature/special"
   val granted: Boolean,            // whether permission is currently granted
   val needsRuntimeRequest: Boolean // true only for "dangerous" permissions
) {
   /** Returns a formatted log-friendly output line. */
   fun toFormattedString(): String =
      "%-65s %-18s %-8s %b".format(
         name,
         protection,
         if (granted) "granted" else "denied",
         needsRuntimeRequest
      )
}
