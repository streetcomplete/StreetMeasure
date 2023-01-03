package de.westnordost.streetmeasure

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.HapticFeedbackConstants.VIRTUAL_KEY
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState.TRACKING
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import de.westnordost.streetmeasure.databinding.ActivityMeasureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/** Activity to measure distances. Can be started as activity for result, which result in slightly
 *  different UX, too.  */
class MeasureActivity : AppCompatActivity(), Scene.OnUpdateListener {

    private val createArCoreSession = ArCoreSessionCreator(this)
    private var initSessionOnResume = true

    private lateinit var binding: ActivityMeasureBinding
    private var arSceneView: ArSceneView? = null

    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(this)

    private var cursorRenderable: Renderable? = null
    private var pointRenderable: Renderable? = null
    private var lineRenderable: Renderable? = null

    private var lineNode: Node? = null
    private var firstNode: AnchorNode? = null
    private var secondNode: Node? = null
    private var cursorNode: AnchorNode? = null

    private var measureVertical: Boolean = false
    private var isFeetInch: Boolean = false
    private var precisionCm: Int = 1
    private var precisionInch: Int = 1
    private var isDisplayUnitFixed: Boolean = false

    private var requestResult: Boolean = false
    private var measuringTapeColor: Int = -1

    private enum class MeasureState { READY, MEASURING, DONE }
    private var measureState: MeasureState = MeasureState.READY

    private var distance: Double = 0.0

    private val displayUnit: MeasureDisplayUnit get() =
        if (isFeetInch) MeasureDisplayUnitFeetInch(precisionInch)
        else MeasureDisplayUnitMeter(precisionCm)

    // taken from https://github.com/streetcomplete/countrymetadata/blob/master/data/lengthUnits.yml
    // in December 2022 (maybe things change in later years, especially in those tiny island countries)
    private val countriesWhereFeetInchIsUsed = setOf(
        "AG", "AS", "BS", "BZ", "DM", "FM", "GD", "GU", "KY", "LC", "LR", "MH", "MP",
        "PR", "PW", "US", "VC", "VG", "VI", "WS"
    )

    /* ---------------------------------------- Lifecycle --------------------------------------- */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // no turning off screen automatically while measuring, also no colored navbar
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
        )
        readIntent()
        distance = 0.0

        try {
            binding = ActivityMeasureBinding.inflate(layoutInflater)
        } catch (e: Exception) {
            /* layout inflation may fail for the ArSceneView for some old devices that don't support
               AR anyway. So we can just exit */
            finish()
            return
        }

        setContentView(binding.root)

        binding.directionButton.isGone = requestResult
        binding.unitButton.isGone = isDisplayUnitFixed

        updateDirectionButtonEnablement()
        updateDirectionButtonImage()
        updateUnitButtonImage()

        binding.startOverButton.setOnClickListener { clearMeasuring() }
        binding.acceptButton.setOnClickListener { returnMeasuringResult() }

        binding.directionButton.setOnClickListener { toggleDirection() }
        binding.unitButton.setOnClickListener { toggleUnit() }

        binding.infoButton.setOnClickListener { InfoDialog(this).show() }

        if (savedInstanceState != null) {
            createArCoreSession.hasRequestedArCoreInstall = savedInstanceState.getBoolean(HAS_REQUESTED_AR_CORE_INSTALL)
        }
    }

    override fun onResume() {
        super.onResume()
        if (initSessionOnResume) {
            lifecycleScope.launch {
                initializeSession()
                initRenderables()
            }
        }
        if (arSceneView != null) {
            try {
                arSceneView?.resume()
                binding.handMotionView.isGone = false
                binding.trackingMessageTextView.isGone = true
            } catch (e: CameraNotAvailableException) {
                // without camera, we can't do anything, might as well quit
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        arSceneView?.pause()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putBoolean(HAS_REQUESTED_AR_CORE_INSTALL, createArCoreSession.hasRequestedArCoreInstall)
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView?.pause()
        arSceneView?.destroy()
        // closing can take several seconds, should be done one background thread that outlives this activity
        GlobalScope.launch(Dispatchers.Default) {
            arSceneView?.session?.close()
        }
    }

    private fun readIntent() {
        requestResult = intent.getBooleanExtra(PARAM_REQUEST_RESULT, false)
        measureVertical = intent.getBooleanExtra(PARAM_MEASURE_VERTICAL, measureVertical)

        val displayUnitStr = intent.getStringExtra(PARAM_UNIT)
        isFeetInch = when (displayUnitStr) {
            UNIT_METER -> false
            UNIT_FOOT_AND_INCH -> true
            else -> prefs.getBoolean(
                PREF_IS_FT_IN,
                resources.configuration.locales.get(0).country in countriesWhereFeetInchIsUsed
            )
        }
        isDisplayUnitFixed = when (displayUnitStr) {
            UNIT_METER, UNIT_FOOT_AND_INCH -> true
            else -> false
        }

        precisionCm = intent.getIntExtra(PARAM_PRECISION_CM, 1).coerceIn(1, 100)
        precisionInch = intent.getIntExtra(PARAM_PRECISION_INCH, 1).coerceIn(1, 12)

        val measuringTapeColorInt = intent.getIntExtra(PARAM_MEASURING_TAPE_COLOR, -1)
        measuringTapeColor = if (measuringTapeColorInt == -1) {
            android.graphics.Color.argb(255, 209, 64, 0)
        } else measuringTapeColorInt
    }

    /* ---------------------------------------- Buttons ----------------------------------------- */

    private fun toggleDirection() {
        measureVertical = !measureVertical
        binding.directionButtonImage.animate()
            .rotation(if (measureVertical) 90f else 0f)
            .setDuration(150)
            .start()
    }

    private fun toggleUnit() {
        binding.unitButtonImage.flip(150) {
            isFeetInch = !isFeetInch
            prefs.edit {
                putBoolean(PREF_IS_FT_IN, isFeetInch)
            }
            updateMeasurementTextView()
            updateUnitButtonImage()
        }
        binding.measurementTextView.flip(150)
    }

    private fun updateDirectionButtonEnablement() {
        binding.directionButton.isEnabled = measureState != MeasureState.MEASURING
    }

    private fun updateDirectionButtonImage() {
        binding.directionButtonImage.rotation = if (measureVertical) 90f else 0f
    }

    private fun updateUnitButtonImage() {
        binding.unitButtonImage.setImageResource(when (displayUnit) {
            is MeasureDisplayUnitFeetInch -> R.drawable.ic_foot_24
            is MeasureDisplayUnitMeter ->    R.drawable.ic_meter_24
        })
    }

    /* --------------------------------- Scene.OnUpdateListener --------------------------------- */

    override fun onUpdate(frameTime: FrameTime) {
        val frame = arSceneView?.arFrame ?: return

        if (frame.hasFoundPlane()) {
            binding.handMotionView.isGone = true
        }

        setTrackingMessage(frame.camera.trackingFailureReason.messageResId)

        if (frame.camera.trackingState == TRACKING) {
            if (measureVertical) {
                if (measureState == MeasureState.READY) {
                    hitPlaneAndUpdateCursor(frame)
                } else if (measureState == MeasureState.MEASURING) {
                    updateVerticalMeasuring(frame.camera.displayOrientedPose)
                }
            } else {
                hitPlaneAndUpdateCursor(frame)
            }
        }
    }

    private fun hitPlaneAndUpdateCursor(frame: Frame) {
        val centerX = binding.arSceneViewContainer.width / 2f
        val centerY = binding.arSceneViewContainer.height / 2f
        val hitResults = frame.hitTest(centerX, centerY).filter {
            (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true
        }
        val firstNode = firstNode
        val hitResult = if (firstNode == null) {
            hitResults.firstOrNull()
        } else {
            /* after first node is placed on the plane, only accept hits with (other) planes
               that are more or less on the same height */
            hitResults.find { abs(it.hitPose.ty() - firstNode.worldPosition.y) < 0.1 }
        }

        if (hitResult != null) {
            updateCursor(hitResult)
            setTrackingMessage(
                if (measureState == MeasureState.READY) R.string.ar_core_tracking_hint_tap_to_measure else null
            )
        } else {
            /* when no plane can be found at the cursor position and the camera angle is
               shallow enough, display a hint that user should cross street
             */
            val cursorDistanceFromCamera = cursorNode?.worldPosition?.let {
                Vector3.subtract(frame.camera.pose.position, it).length()
            } ?: 0f

            setTrackingMessage(
                if (cursorDistanceFromCamera > 3f) R.string.ar_core_tracking_error_no_plane_hit else null
            )
        }
    }

    private fun setTrackingMessage(messageResId: Int?) {
        binding.trackingMessageTextView.isGone = messageResId == null
        messageResId?.let { binding.trackingMessageTextView.setText(messageResId) }
    }

    /* ------------------------------------------ Session --------------------------------------- */

    private suspend fun initializeSession() {
        initSessionOnResume = false
        val result = createArCoreSession()
        if (result is ArCoreSessionCreator.Success) {
            val session = result.session
            configureSession(session)
            addArSceneView(session)
        } else if (result is ArCoreSessionCreator.Failure) {
            val reason = result.reason
            if (reason == ArNotAvailableReason.AR_CORE_SDK_TOO_OLD) {
                Toast.makeText(this, R.string.ar_core_error_sdk_too_old, Toast.LENGTH_SHORT).show()
            } else if (reason == ArNotAvailableReason.NO_CAMERA_PERMISSION) {
                Toast.makeText(this, R.string.no_camera_permission_toast, Toast.LENGTH_SHORT).show()
            }
            // otherwise nothing we can do here...
            finish()
        } else {
            // and if it is null, we remember that we want to continue the session creation on
            // next onResume
            initSessionOnResume = true
        }
    }

    private fun configureSession(session: Session) {
        val config = Config(session)

        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE // necessary for Sceneform
        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        // disabling unused features should make processing faster
        config.depthMode = Config.DepthMode.DISABLED
        config.cloudAnchorMode = Config.CloudAnchorMode.DISABLED
        config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED

        session.configure(config)
    }

    private fun addArSceneView(session: Session) {
        val arSceneView = ArSceneView(this)
        arSceneView.planeRenderer.isEnabled = false
        binding.arSceneViewContainer.addView(arSceneView, MATCH_PARENT, MATCH_PARENT)
        arSceneView.setupSession(session)
        arSceneView.scene.addOnUpdateListener(this)
        arSceneView.setOnClickListener { onTapPlane() }
        this.arSceneView = arSceneView
    }

    /* ---------------------------------------- Measuring --------------------------------------- */

    private fun onTapPlane() {
        when (measureState) {
            MeasureState.READY -> {
                startMeasuring()
            }
            MeasureState.MEASURING -> {
                measuringDone()
            }
            MeasureState.DONE -> {
                /* different behavior: When caller requests result, tapping again doesn't clear the
                 * result, instead the user needs to tap on the "start over" button, like when
                 * taking a picture with the camera */
                if (!requestResult) clearMeasuring() else continueMeasuring()
            }
        }
    }

    private suspend fun initRenderables() {
        // takes about half a second on a high-end device(!)
        val materialBlue = MaterialFactory.makeOpaqueWithColor(this, Color(measuringTapeColor)).await()
        cursorRenderable = ViewRenderable.builder().setView(this, R.layout.view_ar_cursor).build().await()
        pointRenderable = ShapeFactory.makeCylinder(0.03f, 0.005f, Vector3.zero(), materialBlue)
        lineRenderable = ShapeFactory.makeCube(Vector3(0.02f, 0.005f, 1f), Vector3.zero(), materialBlue)
        listOfNotNull(cursorRenderable, pointRenderable, lineRenderable).forEach {
            it.isShadowCaster = false
            it.isShadowReceiver = false
        }
        // in case they have been initialized already, (re)set renderables...
        cursorNode?.renderable = cursorRenderable
        firstNode?.renderable = pointRenderable
        secondNode?.renderable = pointRenderable
        lineNode?.renderable = lineRenderable
    }

    private fun startMeasuring() {
        val anchor = cursorNode?.anchor ?: return
        measureState = MeasureState.MEASURING
        updateDirectionButtonEnablement()
        binding.arSceneViewContainer.performHapticFeedback(VIRTUAL_KEY)
        firstNode = AnchorNode().apply {
            renderable = pointRenderable
            setParent(arSceneView!!.scene)
            setAnchor(anchor)
        }

        if (measureVertical) {
            secondNode = Node()
            cursorNode?.isEnabled = false
        } else {
            secondNode = AnchorNode().apply { setAnchor(anchor) }
        }
        secondNode?.apply {
            renderable = pointRenderable
            setParent(arSceneView!!.scene)
        }
    }

    private fun measuringDone() {
        binding.arSceneViewContainer.performHapticFeedback(VIRTUAL_KEY)
        if (requestResult) binding.acceptResultContainer.isGone = false
        measureState = MeasureState.DONE
        updateDirectionButtonEnablement()
    }

    private fun continueMeasuring() {
        binding.arSceneViewContainer.performHapticFeedback(VIRTUAL_KEY)
        if (requestResult) binding.acceptResultContainer.isGone = true
        measureState = MeasureState.MEASURING
        updateDirectionButtonEnablement()
    }

    private fun clearMeasuring() {
        measureState = MeasureState.READY
        updateDirectionButtonEnablement()
        binding.arSceneViewContainer.performHapticFeedback(VIRTUAL_KEY)
        binding.measurementSpeechBubble.isInvisible = true
        binding.acceptResultContainer.isGone = true
        distance = 0.0
        cursorNode?.isEnabled = true
        firstNode?.anchor?.detach()
        firstNode?.setParent(null)
        firstNode = null
        (secondNode as? AnchorNode)?.anchor?.detach()
        secondNode?.setParent(null)
        secondNode = null
        lineNode?.setParent(null)
        lineNode = null
    }

    private fun returnMeasuringResult() {
        val resultIntent = Intent(RESULT_ACTION)
        when (val displayUnit = displayUnit) {
            is MeasureDisplayUnitFeetInch -> {
                val (feet, inches) = displayUnit.getRounded(distance)
                resultIntent.putExtra(RESULT_FEET, feet)
                resultIntent.putExtra(RESULT_INCHES, inches)
            }
            is MeasureDisplayUnitMeter -> {
                resultIntent.putExtra(RESULT_METERS, displayUnit.getRounded(distance))
            }
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun updateCursor(hitResult: HitResult) {
        // release previous anchor only if it is not used by any other node
        val anchor = cursorNode?.anchor
        if (anchor != null && anchor != firstNode?.anchor && anchor != (secondNode as? AnchorNode)?.anchor) {
            anchor.detach()
        }

        try {
            val newAnchor = hitResult.createAnchor()
            val cursorNode = getCursorNode()
            cursorNode.anchor = newAnchor

            if (measureState == MeasureState.MEASURING) {
                (secondNode as? AnchorNode)?.anchor = newAnchor
                updateDistance()
            }
        } catch (e: Exception) {
            Log.e("MeasureActivity", "Error", e)
        }
    }

    private fun updateVerticalMeasuring(cameraPose: Pose) {
        val cameraPos = cameraPose.position
        val nodePos = firstNode!!.worldPosition

        val cameraToNodeHeightDifference = cameraPos.y - nodePos.y
        val cameraToNodeDistanceOnPlane = sqrt((cameraPos.x - nodePos.x).pow(2) + (cameraPos.z - nodePos.z).pow(2))
        val cameraAngle = cameraPose.pitch

        val normalizedCameraAngle = normalizeRadians(cameraAngle.toDouble(), -PI)
        val pi2 = PI / 2
        if (normalizedCameraAngle < -pi2 * 2 / 3 || normalizedCameraAngle > +pi2 * 1 / 2) {
            setTrackingMessage(R.string.ar_core_tracking_error_too_steep_angle)
            return
        } else {
            setTrackingMessage(null)
        }

        // don't allow negative heights (into the ground)
        val height = max(0f, cameraToNodeHeightDifference + cameraToNodeDistanceOnPlane * tan(cameraAngle))

        val pos = Vector3.add(nodePos, Vector3(0f, height, 0f))
        secondNode?.worldPosition = pos

        updateDistance()
    }

    private fun updateDistance() {
        val pos1 = firstNode?.worldPosition
        val pos2 = secondNode?.worldPosition
        val up = firstNode?.up
        val hasMeasurement = pos1 != null && pos2 != null && up != null

        binding.measurementSpeechBubble.isInvisible = !hasMeasurement
        if (!hasMeasurement) return

        val difference = Vector3.subtract(pos1, pos2)
        distance = difference.length().toDouble()
        updateMeasurementTextView()

        val line = getLineNode()
        line.worldPosition = Vector3.add(pos1, pos2).scaled(.5f)
        line.worldRotation = Quaternion.lookRotation(difference, up)
        line.localScale = Vector3(1f, 1f, distance.toFloat())
    }

    private fun updateMeasurementTextView() {
        binding.measurementTextView.text = displayUnit.format(distance)
    }

    private fun getCursorNode(): AnchorNode {
        var node = cursorNode
        if (node == null) {
            node = AnchorNode().apply {
                renderable = cursorRenderable
                setParent(arSceneView!!.scene)
            }
            cursorNode = node
        }
        return node
    }

    private fun getLineNode(): Node {
        var node = lineNode
        if (node == null) {
            node = Node().apply {
                renderable = lineRenderable
                setParent(arSceneView!!.scene)
            }
            lineNode = node
        }
        return node
    }

    /* ----------------------------------------- Intent ----------------------------------------- */

    companion object {
        private const val HAS_REQUESTED_AR_CORE_INSTALL = "has_requested_ar_core_install"

        private const val PREF_IS_FT_IN = "pref_is_ft_in"

        /* --------------------------------- Intent Parameters ---------------------------------- */

        /** Boolean. Whether to measure vertical instead of horizontal distances.
         *  Default is to measure horizontal. */
        const val PARAM_MEASURE_VERTICAL = "measure_vertical"

        /** String. Specifies which unit should be used for display and result returned.
         *  Either UNIT_METER or UNIT_FOOT_AND_INCH. If it is not defined, a unit is
         *  selected based on the user's locale and he is able to switch between units
         */
        const val PARAM_UNIT = "unit"

        const val UNIT_METER = "meter"
        const val UNIT_FOOT_AND_INCH = "foot_and_inch"

        /** Boolean. Whether this activity should return a result. If yes, the activity will return
         *  the measure result in RESULT_MEASURE_METERS or RESULT_MEASURE_FEET + RESULT_MEASURE_INCHES
         */
        const val PARAM_REQUEST_RESULT = "request_result"

        /** Int. The precision in centimeters if PARAM_UNIT = UNIT_METER to which the measure result
         *  is rounded.
         *
         *  For measuring widths along several meters (road widths), it is recommended to use 10 cm,
         *  because a higher precision cannot be achieved on average with ARCore anyway
         *  and displaying the value in that precision may give a false sense that the measurement
         *  is that precise.
         */
        const val PARAM_PRECISION_CM = "precision_cm"

        /** Int. The precision in inches if PARAM_UNIT = UNIT_FOOT_AND_INCH to which the measure
         *  result is rounded.
         *
         *  For measuring widths along several meters (road widths), it is recommended to use 4
         *  inches, because a higher precision cannot be achieved on average with ARCore anyway and
         *  displaying the value in that precision may give a false sense that the measurement is
         *  that precise.
         * */
        const val PARAM_PRECISION_INCH = "precision_inch"

        /** Int. Color value of the measuring tape. Default is orange.
         */
        const val PARAM_MEASURING_TAPE_COLOR = "measuring_tape_color"

        /* ----------------------------------- Intent Result ------------------------------------ */

        /** The action to identify a result */
        const val RESULT_ACTION = "de.westnordost.streetmeasure.RESULT_ACTION"

        /** The result as displayed to the user, set if display unit was meters. Double. */
        const val RESULT_METERS = "meters"

        /** The result as displayed to the user, set if display unit was feet+inches. Int. */
        const val RESULT_FEET = "feet"

        /** The result as displayed to the user, set if display unit was feet+inches. Int. */
        const val RESULT_INCHES = "inches"
    }
}
