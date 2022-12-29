package de.westnordost.streetmeasure

import android.app.AlertDialog
import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import androidx.core.text.HtmlCompat
import de.westnordost.streetmeasure.databinding.DialogInfoBinding

class InfoDialog(context: Context) : AlertDialog(context) {
    init {
        val binding = DialogInfoBinding.inflate(LayoutInflater.from(context))

        val html =
            "<h3>" + context.getString(R.string.measure_info_title) + "</h3>" +
            context.getString(R.string.measure_info_html_description) +
            "<h3>" + context.getString(R.string.about_title_privacy_statement) + "</h3>" +
            context.getString(R.string.privacy_html_arcore)

        binding.textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.textView.movementMethod = LinkMovementMethod.getInstance()
        setView(binding.root)
        setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok)) { _, _ ->  }
    }
}