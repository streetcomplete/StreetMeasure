package de.westnordost.streetmeasure

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.ColorInt

sealed interface Length
data class LengthInMeters(val meters: Double) : Length
data class LengthInFeetAndInches(val feet: Int, val inches: Int) : Length

enum class LengthUnit { METER, FOOT_AND_INCH }

class MeasureContract : ActivityResultContract<MeasureContract.Params, Length?>() {
    data class Params(
        /** Specifies which unit should be used for display and result returned. If it is not
         *  defined, a unit is selected based on the user's locale and he is able to switch between
         *  units. */
        val lengthUnit: LengthUnit? = null,
        /** The precision in centimeters if lengthUnit = METER to which the measure result is
         *  rounded.
         *
         *  For measuring widths along several meters (road widths), it is recommended to use 10 cm,
         *  because a higher precision cannot be achieved on average with ARCore anyway
         *  and displaying the value in that precision may give a false sense that the measurement
         *  is that precise.
         */
        val precisionCm: Int? = null,
        /** The precision in inches if lengthUnit = FOOT_AND_INCH to which the measure result is
         *  rounded.
         *
         *  For measuring widths along several meters (road widths), it is recommended to use 4
         *  inches, because a higher precision cannot be achieved on average with ARCore anyway and
         *  displaying the value in that precision may give a false sense that the measurement is
         *  that precise.
         * */
        val precisionInch: Int? = null,
        /** Whether to measure vertical instead of horizontal distances. */
        val measureVertical: Boolean = false,
        /** Custom measuring tape color as ARGB color int. Default is orange. */
        @ColorInt val measuringTapeColor: Int? = null,
    )

    override fun createIntent(context: Context, input: Params): Intent {
        val unit = when (input.lengthUnit) {
            LengthUnit.METER ->         "meter"
            LengthUnit.FOOT_AND_INCH -> "foot_and_inch"
            null -> null
        }
        val intent = context.packageManager.getLaunchIntentForPackage("de.westnordost.streetmeasure")
            ?: throw ActivityNotFoundException()
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("request_result", true)
        intent.putExtra("unit", unit)
        intent.putExtra("precision_cm", input.precisionCm)
        intent.putExtra("precision_inch", input.precisionInch)
        intent.putExtra("measure_vertical", input.measureVertical)
        intent.putExtra("measuring_tape_color", input.measuringTapeColor)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Length? {
        if (resultCode != Activity.RESULT_OK) return null

        val meters = intent?.getDoubleExtra("meters", -1.0)?.takeIf { it != -1.0 }
        if (meters != null) return LengthInMeters(meters)

        val feet = intent?.getIntExtra("feet", -1)?.takeIf { it != -1 }
        val inches = intent?.getIntExtra("inches", -1)?.takeIf { it != -1 }
        if (feet != null && inches != null) return LengthInFeetAndInches(feet, inches)

        return null
    }
}
