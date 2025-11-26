package de.rogallab.mobile.ui.permissions.helpers

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.rogallab.mobile.ui.navigation.INavHandler

/**
 * Permission Request Algorithm (Generic Flow)
 *
 * This screen demonstrates the full Just-In-Time (JIT) permission workflow
 * using RequirePermissions() and a small amount of local UI state.
 *
 * The algorithm follows these steps:
 *
 * 1. USER TRIGGER
 *    - The user presses a button ("Open Camera", "Open Gallery").
 *    - We set a local flag (e.g., askCamera = true).
 *    - This causes RequirePermissions() to enter the composition.
 *
 * 2. CHECK CURRENT PERMISSION STATE
 *    - RequirePermissions() inspects which permissions are missing.
 *    - If all permissions are already granted:
 *         → immediately call onAllGranted()
 *         → close the request flag and continue the feature flow.
 *
 * 3. SYSTEM PERMISSION REQUEST
 *    - If one or more permissions are missing:
 *         → RequestMultiplePermissions() is launched.
 *
 * 4. RESULT HANDLING
 *    - The launcher callback returns a map: { permission → granted/denied }.
 *
 *    a) ALL GRANTED →
 *         - Call onAllGranted()
 *         - Reset the askX flag
 *         - Proceed with the feature (camera/gallery/...).
 *
 *    b) ONE OR MORE DENIED →
 *         i. TEMPORARILY DENIED (shouldShowRequestPermissionRationale = true)
 *             - showRationale(permission) is invoked
 *             - The composable shows a rationale dialog
 *             - If the user confirms:
 *                   → set askX = true to retry the request
 *
 *         ii. PERMANENTLY DENIED
 *             (shouldShowRequestPermissionRationale = false immediately after request)
 *             - Set showPermanentlyDeniedDialog = true
 *             - Ask the user to manually enable the permission in app settings
 *             - If the user accepts:
 *                   → context.openAppSettings()
 *             - The screen stays in a safe state until the user returns
 *
 * 5. RATIONALE DIALOG
 *    - Used only when the user denied the permission *without* selecting
 *      “Don’t ask again”.
 *    - Explains why the permission is required.
 *    - If the user accepts the explanation:
 *         → Restart the permission request (askX = true)
 *
 * 6. PERMANENTLY DENIED DIALOG
 *    - Shown when the user checked “Don’t ask again” or the system decides
 *      no further rationale should be shown.
 *    - The only valid next step is opening the app settings.
 *
 * 7. RETRY LOOP
 *    - After returning from app settings, if the user granted the permission,
 *      pressing the main button will activate the full feature immediately.
 *
 * 8. UI SEPARATION
 *    - The permission UI (dialogs) is kept in local state (rationalePermission, permanentlyDenied)
 *    - The feature logic (camera, gallery, etc.) is invoked only after all permissions
 *      are confirmed as granted.
 *
 * Summary:
 *    The composable manages a controlled permission flow with clear state transitions:
 *        Trigger → RequirePermissions → Result → Rationale/Settings → Retry or Success.
 *
 */

/*
To use it in Navigation or UI
GalleryPermissionScreen(
    onGalleryReady = {
        // open your gallery picker or image selector
        navController.navigate("gallery-screen")
    }
)
*/

@Composable
fun GalleryPermissionScreen(
   navHandler: INavHandler
) {
   var askGallery by remember { mutableStateOf(false) }
   var rationalePermission by remember { mutableStateOf<String?>(null) }
   var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }

   val context = LocalContext.current

   // Entry UI
   Button(onClick = { askGallery = true }) {
      Text("Open Gallery")
   }

   // Permission Flow
   if (askGallery) {
      RequirePermissions(
         PermissionRequestConfig(
            permissions = mediaReadImagesPerms(), // API-aware
            onAllGranted = {
               askGallery = false
               //navHandler.push(Gallery)   // <-- Navigation 3
            },
            onDenied = { _, permanently ->
               askGallery = false
               if (permanently) showPermanentlyDeniedDialog = true
            },
            showRationale = { perm ->
               rationalePermission = perm
            }
         )
      )
   }

   // --- Rationale dialog ---
   rationalePermission?.let { perm ->
      PermissionRationaleDialog(
         permission = perm,
         message = "Photo access is required to pick an image from the gallery.",
         onConfirm = {
            rationalePermission = null
            askGallery = true
         },
         onDismiss = {
            rationalePermission = null
         }
      )
   }

   // --- Permanently-denied dialog ---
   if (showPermanentlyDeniedDialog) {
      AlertDialog(
         onDismissRequest = { showPermanentlyDeniedDialog = false },
         title = { Text("Gallery permission permanently denied") },
         text = {
            Text("Please open app settings and enable photo access.")
         },
         confirmButton = {
            Button(
               onClick = {
                  showPermanentlyDeniedDialog = false
                  context.openAppSettings()
               }
            ) { Text("Open settings") }
         },
         dismissButton = {
            Button(onClick = { showPermanentlyDeniedDialog = false }) {
               Text("Cancel")
            }
         }
      )
   }
}