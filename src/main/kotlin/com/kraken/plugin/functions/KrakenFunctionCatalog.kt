package com.kraken.plugin.functions

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Catalogue figé des fonctions natives de KEL.
 *
 * Le moteur Kraken découvre ses fonctions à l'exécution (`ServiceLoader` sur
 * `FunctionLibrary`, cf. `kraken.el.functionregistry.FunctionRegistry`).
 * RuleScribe n'exécute rien et ne dépend pas du moteur à la compilation : il
 * embarque donc une copie du catalogue, générée depuis les sources par
 * `tools/gen_functions.py` et jamais éditée à la main.
 *
 * Le moteur identifie une fonction par son **nom et son nombre de paramètres**
 * — pas par les types. Deux surcharges de même arité ne coexistent pas, d'où
 * [find] qui renvoie au plus un résultat.
 */
object KrakenFunctionCatalog {

    /** Toutes les fonctions natives, triées par nom puis par arité. */
    val functions: List<KelFunction> by lazy { load().functions }

    /** Bibliothèques (Math, String, Date…) et leur description. */
    val libraries: List<KelLibrary> by lazy { load().libraries }

    private val byName: Map<String, List<KelFunction>> by lazy {
        functions.groupBy { it.name }
    }

    /** Surcharges portant ce nom, quelle que soit leur arité. */
    fun byName(name: String): List<KelFunction> = byName[name].orEmpty()

    /** La fonction correspondant exactement à l'identité (nom, arité) du moteur. */
    fun find(name: String, arity: Int): KelFunction? =
        byName(name).firstOrNull { it.parameters.size == arity }

    private fun load(): Catalog =
        KrakenFunctionCatalog::class.java.getResourceAsStream(RESOURCE)
            ?.reader(Charsets.UTF_8)
            ?.use { Gson().fromJson(it, Catalog::class.java) }
            ?: Catalog(emptyList(), emptyList())

    private const val RESOURCE = "/functions/kel-functions.json"

    private class Catalog(val libraries: List<KelLibrary>, val functions: List<KelFunction>)
}

class KelLibrary(
    val name: String,
    val description: String?,
    val since: String?,
)

class KelFunction(
    val name: String,
    val library: String,
    val parameters: List<KelParameter>,
    @SerializedName("returnType") val returnType: String,
    val description: String?,
    val since: String?,
    val examples: List<KelExample>,
) {
    /** `Round(Number number, Number scale) : Number` */
    fun signature(): String =
        "$name(${parameters.joinToString(", ") { it.presentation() }}) : $returnType"
}

class KelParameter(
    val name: String,
    val type: String,
    val required: Boolean,
) {
    fun presentation(): String = "$type $name"
}

class KelExample(
    val expression: String,
    val result: String?,
)
