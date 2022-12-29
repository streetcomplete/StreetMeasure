package de.westnordost.streetmeasure

import android.Manifest.permission.CAMERA
import android.app.ActivityManager
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability.UNKNOWN_CHECKING
import com.google.ar.core.ArCoreApk.Availability.UNKNOWN_TIMED_OUT
import com.google.ar.core.ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import de.westnordost.streetmeasure.ArNotAvailableReason.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Creates an ARCore session and ensures that everything is set up to be able to use AR.
 *  It is a state machine.
 *
 *  - Checks the OpenGL ES version
 *  - Checks if this device is compatible with AR
 *  - Checks if ARCore has been installed, is up-to-date and if not, requests the user to do this
 *  - Checks for camera permission and requests it if it is not granted
 *  - Checks if the ARCore SDK used is still compatible with the current ARCore installation
 */
class ArCoreSessionCreator(
    private val activity: AppCompatActivity,
    private val features: Set<Session.Feature> = setOf(),
) {
    var hasRequestedArCoreInstall: Boolean = false

    /** Returns an ARCore session (after some back and forth with the user) or a reason why it can't
     *  be created. It returns null if we have to wait for something potentially outside of our
     *  lifecycle (installing ARCore APK, requesting permissions) */
    suspend operator fun invoke(): Result? {

        if (activity.getSystemService<ActivityManager>()!!.deviceConfigurationInfo.glEsVersion.toDouble() < 3.1) {
            return Failure(DEVICE_NOT_COMPATIBLE)
        }

        val availability = ArCoreApk.getInstance().getAvailability(activity)
        if (!availability.isSupported) {
            return Failure(when (availability) {
                UNKNOWN_CHECKING, UNKNOWN_TIMED_OUT -> DEVICE_COMPATIBILITY_CHECK_TIMED_OUT
                UNSUPPORTED_DEVICE_NOT_CAPABLE ->      DEVICE_NOT_COMPATIBLE
                else ->                                DEVICE_COMPATIBILITY_CHECK_FAILURE
            })
        }

        try {
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, !hasRequestedArCoreInstall)
            hasRequestedArCoreInstall = true
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                // we are done for now, need to wait until installed
                return null
            }
        } catch (e: UnavailableDeviceNotCompatibleException) {
            return Failure(DEVICE_NOT_COMPATIBLE)
        } catch (e: UnavailableUserDeclinedInstallationException) {
            return Failure(AR_CORE_APK_NOT_INSTALLED_OR_TOO_OLD)
        }

        if (ContextCompat.checkSelfPermission(activity, CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA)) {
                if (!askUserToAcknowledgeCameraPermissionRationale()) {
                    return Failure(NO_CAMERA_PERMISSION)
                }
            }
            // permissions requested, we are done for now
            activity.requestPermissions(arrayOf(CAMERA), 1)
            return null
        }

        try {
            return Success(Session(activity, features))
        } catch (e: UnavailableSdkTooOldException) {
            return Failure(AR_CORE_SDK_TOO_OLD)
        } catch (e: UnavailableDeviceNotCompatibleException) {
            return Failure(DEVICE_NOT_COMPATIBLE)
        } catch (e: SecurityException) {
            return Failure(NO_CAMERA_PERMISSION)
        }
    }

    /** Show dialog that explains why the camera permission is necessary. Returns whether the user
     *  acknowledged the rationale. */
    private suspend fun askUserToAcknowledgeCameraPermissionRationale(): Boolean =
        suspendCancellableCoroutine { cont ->
            val dlg = AlertDialog.Builder(activity)
                .setTitle(R.string.no_camera_permission_warning_title)
                .setMessage(R.string.no_camera_permission_warning)
                .setPositiveButton(android.R.string.ok) { _, _ -> cont.resume(true) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> cont.resume(false) }
                .setOnCancelListener { cont.resume(false) }
                .create()
            cont.invokeOnCancellation { dlg.cancel() }
            dlg.show()
        }

    sealed interface Result
    data class Success(val session: Session) : Result
    data class Failure(val reason: ArNotAvailableReason) : Result
}

enum class ArNotAvailableReason {
    DEVICE_COMPATIBILITY_CHECK_TIMED_OUT,
    DEVICE_COMPATIBILITY_CHECK_FAILURE,
    DEVICE_NOT_COMPATIBLE,
    NO_CAMERA_PERMISSION,
    AR_CORE_APK_NOT_INSTALLED_OR_TOO_OLD,
    AR_CORE_SDK_TOO_OLD
}
