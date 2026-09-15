package com.kraken.plugin

import junit.framework.TestCase
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Default colours shipped through `additionalTextAttributes`.
 *
 * Without that file the depth keys exist but paint nothing, and no test on emitted
 * ranges would notice, so this checks the artifact itself. The invariant that matters:
 * red is reserved for unmatched braces.
 */
class KrakenColorSchemeTest : TestCase() {

    private fun foregrounds(resource: String): Map<String, String> {
        val stream = javaClass.getResourceAsStream(resource)
            ?: error("$resource is missing from the resources")
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

    /** Red-dominant: the R component is clearly above G and B. */
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
            val value = colors[key] ?: fail("$key has no colour in $resource").let { return }
            assertFalse(
                "$resource: red is reserved for unmatched brackets, but $key is $value",
                isRed(value),
            )
        }
        val unmatched = colors["KRAKEN_UNMATCHED_BRACKET"]
            ?: fail("KRAKEN_UNMATCHED_BRACKET has no colour in $resource").let { return }
        assertTrue(
            "$resource: an unmatched bracket must be red, not $unmatched",
            isRed(unmatched),
        )
    }

    fun testDefaultScheme() = checkScheme("/colorSchemes/KrakenDefault.xml")

    fun testDarculaScheme() = checkScheme("/colorSchemes/KrakenDarcula.xml")

    fun testDepthColoursAreDistinct() {
        for (resource in listOf("/colorSchemes/KrakenDefault.xml", "/colorSchemes/KrakenDarcula.xml")) {
            val depths = (1..3).mapNotNull { foregrounds(resource)["KRAKEN_BRACKET_DEPTH_$it"] }
            assertEquals("$resource : trois teintes distinctes attendues", 3, depths.distinct().size)
        }
    }
}
