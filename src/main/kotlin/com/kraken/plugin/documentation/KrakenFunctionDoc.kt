package com.kraken.plugin.documentation

import com.intellij.openapi.util.text.StringUtil
import com.kraken.plugin.functions.KelFunction
import com.kraken.plugin.psi.KrakenFunctionDecl

/**
 * Rendu de la doc rapide des fonctions.
 *
 * Deux provenances, présentées de la même façon pour que l'utilisateur n'ait
 * pas à savoir laquelle il consulte : les natives viennent du catalogue
 * embarqué (métadonnées extraites des annotations Java du moteur), les
 * déclarées viennent de leur commentaire `/** … */`.
 *
 * Les balises reconnues dans un commentaire suivent `FunctionDoc.g4` du
 * moteur : `@since`, `@example`, `@result`, `@invalidExample`, `@parameter`.
 */
internal object KrakenFunctionDoc {

    fun render(function: KelFunction): String = buildString {
        append("<b>Function</b> <code>").append(escape(function.signature())).append("</code>")
        append("<br/><i>").append(escape(function.library)).append(" (built-in)</i>")
        function.description?.takeIf { it.isNotBlank() }?.let {
            append("<br/><br/>").append(escape(it))
        }
        val documented = function.parameters.filter { it.name.isNotBlank() }
        if (documented.isNotEmpty()) {
            append("<br/><br/><b>Parameters</b>")
            for (parameter in documented) {
                append("<br/><code>").append(escape(parameter.name)).append("</code> : ")
                append(escape(parameter.type))
                if (parameter.required) append(" <i>(required)</i>")
            }
        }
        if (function.examples.isNotEmpty()) {
            append("<br/><br/><b>Examples</b>")
            for (example in function.examples) {
                append("<br/><code>").append(escape(example.expression)).append("</code>")
                example.result?.let { append(" &rarr; <code>").append(escape(it)).append("</code>") }
            }
        }
        function.since?.let { append("<br/><br/><i>Since ").append(escape(it)).append("</i>") }
    }

    fun render(declaration: KrakenFunctionDecl): String = buildString {
        append("<b>Function</b> <code>").append(escape(declaration.signature())).append("</code>")
        append("<br/><i>").append(escape(declaration.containingFile.name))
        if (!declaration.hasBody()) {
            // Une signature nue délègue au Java enregistré : le dire évite de
            // chercher un corps KEL qui n'existe pas.
            append(" — signature only, implemented in Java")
        }
        append("</i>")

        val doc = declaration.docComment()?.text?.let(::parse) ?: return@buildString
        doc.description.takeIf { it.isNotBlank() }?.let { append("<br/><br/>").append(escape(it)) }
        if (doc.parameters.isNotEmpty()) {
            append("<br/><br/><b>Parameters</b>")
            for ((name, description) in doc.parameters) {
                append("<br/><code>").append(escape(name)).append("</code> — ").append(escape(description))
            }
        }
        if (doc.examples.isNotEmpty()) {
            append("<br/><br/><b>Examples</b>")
            for ((expression, result) in doc.examples) {
                append("<br/><code>").append(escape(expression)).append("</code>")
                result?.let { append(" &rarr; <code>").append(escape(it)).append("</code>") }
            }
        }
        doc.since?.let { append("<br/><br/><i>Since ").append(escape(it)).append("</i>") }
    }

    class DocComment(
        val description: String,
        val since: String?,
        val parameters: List<Pair<String, String>>,
        val examples: List<Pair<String, String?>>,
    )

    /** Analyse un commentaire de doc selon les balises de `FunctionDoc.g4`. */
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
                trimmed.startsWith("@") -> Unit // balise non reconnue : ignorée
                else -> if (trimmed.isNotEmpty()) description.append(trimmed).append(' ')
            }
        }
        return DocComment(description.toString().trim(), since, parameters, examples)
    }

    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)
}
