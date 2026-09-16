package com.kraken.plugin.documentation

import com.intellij.openapi.util.text.StringUtil
import com.kraken.plugin.functions.KelFunction
import com.kraken.plugin.psi.KrakenFunctionDecl

/**
 * Quick documentation rendering for functions.
 *
 * Natives come from the bundled catalogue (metadata extracted from the engine's Java
 * annotations) and declared functions from their doc comment; both render the same
 * way. Doc comment tags follow the engine's `FunctionDoc.g4`: `@since`, `@example`,
 * `@result`, `@invalidExample`, `@parameter`.
 */
internal object KrakenFunctionDoc {

    fun render(function: KelFunction): String = render(
        signature = function.signature(),
        origin = escape(function.library) + " (built-in)",
        description = function.description,
        parameters = function.parameters
            .filter { it.name.isNotBlank() }
            .map { it.name to " : " + escape(it.type) + if (it.required) " <i>(required)</i>" else "" },
        examples = function.examples.map { it.expression to it.result },
        since = function.since,
    )

    fun render(declaration: KrakenFunctionDecl): String {
        val origin = escape(declaration.containingFile.name) + if (declaration.hasBody()) "" else SIGNATURE_ONLY
        val doc = declaration.docComment()?.text?.let(::parse) ?: EMPTY_DOC
        return render(
            signature = declaration.signature(),
            origin = origin,
            description = doc.description,
            parameters = doc.parameters.map { (name, description) -> name to " — " + escape(description) },
            examples = doc.examples,
            since = doc.since,
        )
    }

    /** [parameters] pairs a name with the HTML that follows it on its line. */
    private fun render(
        signature: String,
        origin: String,
        description: String?,
        parameters: List<Pair<String, String>>,
        examples: List<Pair<String, String?>>,
        since: String?,
    ): String = buildString {
        append("<b>Function</b> <code>").append(escape(signature)).append("</code>")
        append("<br/><i>").append(origin).append("</i>")
        description?.takeIf { it.isNotBlank() }?.let { append("<br/><br/>").append(escape(it)) }
        if (parameters.isNotEmpty()) {
            append("<br/><br/><b>Parameters</b>")
            for ((name, detail) in parameters) {
                append("<br/><code>").append(escape(name)).append("</code>").append(detail)
            }
        }
        if (examples.isNotEmpty()) {
            append("<br/><br/><b>Examples</b>")
            for ((expression, result) in examples) {
                append("<br/><code>").append(escape(expression)).append("</code>")
                result?.let { append(" &rarr; <code>").append(escape(it)).append("</code>") }
            }
        }
        since?.let { append("<br/><br/><i>Since ").append(escape(it)).append("</i>") }
    }

    /** A bare signature delegates to registered Java code; saying so saves looking for a KEL body. */
    private const val SIGNATURE_ONLY = " — signature only, implemented in Java"

    private val EMPTY_DOC = DocComment("", null, emptyList(), emptyList())

    class DocComment(
        val description: String,
        val since: String?,
        val parameters: List<Pair<String, String>>,
        val examples: List<Pair<String, String?>>,
    )

    fun parse(text: String): DocComment {
        val body = text.removePrefix("/**").removeSuffix("*/")
            .lines()
            .joinToString("\n") { it.trim().removePrefix("*").trim() }
            .trim()

        val description = StringBuilder()
        var since: String? = null
        val parameters = mutableListOf<Pair<String, String>>()
        val examples = mutableListOf<Pair<String, String?>>()

        for (line in body.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("@since") ->
                    since = trimmed.removePrefix("@since").trim().ifBlank { null }

                trimmed.startsWith("@parameter") -> {
                    val rest = trimmed.removePrefix("@parameter").trim()
                    val (name, describes) = rest.split("-", limit = 2)
                        .let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
                    if (name.isNotEmpty()) parameters += name to describes
                }

                trimmed.startsWith("@example") || trimmed.startsWith("@invalidExample") -> {
                    val rest = trimmed.substringAfter(" ", "").trim()
                    if (rest.isNotEmpty()) examples += rest to null
                }

                trimmed.startsWith("@result") -> {
                    val result = trimmed.removePrefix("@result").trim()
                    if (examples.isNotEmpty() && result.isNotEmpty()) {
                        examples[examples.lastIndex] = examples.last().first to result
                    }
                }

                trimmed.startsWith("@") -> Unit

                else -> if (trimmed.isNotEmpty()) description.append(trimmed).append(' ')
            }
        }
        return DocComment(description.toString().trim(), since, parameters, examples)
    }

    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)
}
