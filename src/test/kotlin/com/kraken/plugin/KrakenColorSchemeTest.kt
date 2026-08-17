package com.kraken.plugin

import junit.framework.TestCase
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Les couleurs par défaut livrées via `additionalTextAttributes`.
 *
 * Sans ce fichier, les clés de profondeur existent mais ne peignent rien : la
 * fonctionnalité serait invisible à l'installation, et aucun test sur les
 * plages émises ne le révélerait. On vérifie donc l'artefact lui-même.
 *
 * L'invariant qui compte est celui que la roadmap pose : **le rouge est
 * réservé** aux accolades orphelines. Une couleur qui veut dire « erreur » ne
 * peut pas être aussi une teinte de profondeur, sinon elle ne veut plus rien
 * dire.
 */
class KrakenColorSchemeTest : TestCase() {

    private fun foregrounds(resource: String): Map<String, String> {
        val stream = javaClass.getResourceAsStream(resource)
            ?: error("$resource absent des ressources")
        val doc = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val result = mutableMapOf<String, String>()
        val options = doc.getElementsByTagName("option")
        for (i in 0 until options.length) {
            val node = options.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            if (!name.startsWith("KRAKEN_")) continue
            val inner = (node as org.w3c.dom.Element).getElementsByTagName("option")
            for (j in 0 until inner.length) {
                val opt = inner.item(j).attributes ?: continue
                if (opt.getNamedItem("name")?.nodeValue == "FOREGROUND") {
                    result[name] = opt.getNamedItem("value").nodeValue.uppercase()
                }
            }
        }
        return result
    }

    /** Rouge dominant : composante R nettement au-dessus de V et B. */
    private fun isRed(hex: String): Boolean {
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        return r > g + 60 && r > b + 60
    }

    private fun checkScheme(resource: String) {
        val colors = foregrounds(resource)
        for (depth in 1..3) {
            val key = "KRAKEN_BRACKET_DEPTH_$depth"
            val value = colors[key] ?: fail("$key n'a pas de couleur dans $resource").let { return }
            assertFalse(
                "$resource : le rouge est réservé aux orphelines, $key vaut $value",
                isRed(value)
            )
        }
        val unmatched = colors["KRAKEN_UNMATCHED_BRACKET"]
            ?: fail("KRAKEN_UNMATCHED_BRACKET n'a pas de couleur dans $resource").let { return }
        assertTrue(
            "$resource : une accolade orpheline doit être rouge, pas $unmatched",
            isRed(unmatched)
        )
    }

    fun testDefaultScheme() = checkScheme("/colorSchemes/KrakenDefault.xml")

    fun testDarculaScheme() = checkScheme("/colorSchemes/KrakenDarcula.xml")

    /** Les trois profondeurs doivent se distinguer entre elles. */
    fun testDepthColoursAreDistinct() {
        for (resource in listOf("/colorSchemes/KrakenDefault.xml", "/colorSchemes/KrakenDarcula.xml")) {
            val depths = (1..3).mapNotNull { foregrounds(resource)["KRAKEN_BRACKET_DEPTH_$it"] }
            assertEquals("$resource : trois teintes distinctes attendues", 3, depths.distinct().size)
        }
    }
}
