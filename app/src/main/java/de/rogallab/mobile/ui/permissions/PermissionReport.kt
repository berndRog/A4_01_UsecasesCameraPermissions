package de.rogallab.mobile.ui.permissions
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.core.content.ContextCompat

// build.gradle: targetSdk >= 33/34
// Min SDK doesn't matter; works fully starting from Android M (API 23).
data class PermissionReport(
   val name: String,
   val protection: String,          // "normal" | "dangerous" | "signature/special"
   val granted: Boolean,
   val needsRuntimeRequest: Boolean // true nur für "dangerous"
)

@Suppress("DEPRECATION") // Access to legacy protectionLevel / PROTECTION_MASK_BASE
fun Context.buildPermissionReport(): List<PermissionReport> {
   val pm = packageManager
   val pkg = packageName

   // 1) Manifest-Permissions der App holen
   val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
      pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
   } else {
      @Suppress("DEPRECATION")
      pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
   }

   val requested = pkgInfo.requestedPermissions?.toList().orEmpty()

   return requested.map { perm ->
      // 2) Schutz-Level (protection level) des einzelnen Perms ermitteln
      val pInfo = try { pm.getPermissionInfo(perm, 0) } catch (_: Exception) { null }
      val (protection, needsRuntime) = when {
         pInfo == null -> "unknown" to false
         else -> {
            // Use legacy fields but suppress deprecation at function level
            val base = pInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
            when (base) {
               PermissionInfo.PROTECTION_NORMAL -> "normal" to false
               PermissionInfo.PROTECTION_DANGEROUS -> "dangerous" to true
               PermissionInfo.PROTECTION_SIGNATURE -> "signature/special" to false
               else -> "signature/special" to false // inkl. privileged/oem/appop etc.
            }
         }
      }

      // 3) Aktuellen Grant-Status abfragen
      val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

      PermissionReport(
         name = perm,
         protection = protection,
         granted = granted,
         needsRuntimeRequest = (protection == "dangerous")
      )
   }
}
