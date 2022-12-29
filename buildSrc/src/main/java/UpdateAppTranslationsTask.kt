
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Locale

/** Update the Android string resources (translations) for all the given language codes */
open class UpdateAppTranslationsTask : DefaultTask() {

    @get:Input var projectId: String? = null
    @get:Input var apiToken: String? = null
    @get:Input var targetFiles: ((androidResCode: String) -> String)? = null
    @get:Input var strings: Set<String> = setOf()

    @TaskAction fun run() {
        val targetFiles = targetFiles ?: return
        val apiToken = apiToken ?: return
        val projectId = projectId ?: return

        val languageTags = fetchAvailableLocalizations(apiToken, projectId).map { it.code }
        for (languageTag in languageTags) {
            val androidResCodes = if (languageTag == "en-us") {
                listOf("en", "")
            } else {
                Locale.forLanguageTag(languageTag).transformPOEditorLanguageTag().toAndroidResCodes()
            }

            print(languageTag)
            if (androidResCodes.singleOrNull() != languageTag) print(" -> " + androidResCodes.joinToString(", "))

            // download the translation and save it in the appropriate directory
            val translations = fetchLocalizationJson(apiToken, projectId, languageTag).filterKeys { it in strings }
            // only include complete translations
            if (translations.size < strings.size) {
                println(" (translated ${translations.size}/${strings.size}, skipped)")
                for (androidResCode in androidResCodes) {
                    File(targetFiles(androidResCode)).delete()
                }
                continue
            }
            println()

            val text = """<?xml version="1.0" encoding="utf-8"?>
<resources>
${translations.entries.joinToString("\n") { (key, value) ->
"    <string name=\"$key\">\"${value.escapeXml().replace("\"", "\\\"")}\"</string>"
} }
</resources>"""
            for (androidResCode in androidResCodes) {
                val file = File(targetFiles(androidResCode))
                File(file.parent).mkdirs()
                file.writeText(text)
            }
        }
    }
}
