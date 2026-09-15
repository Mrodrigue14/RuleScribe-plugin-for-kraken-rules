package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.inspection.KrakenUnresolvedIdentifierInspection

/**
 * The unresolved identifier inspection.
 *
 * Without type inference the risk is inventing errors, not missing them, so most of
 * these tests check that the inspection stays silent.
 */
class KrakenUnresolvedIdentifierTest : BasePlatformTestCase() {

    private val model = """
        Root Context Policy {
            String policyCd
            Child AddressInfo
        }

        Context AddressInfo {
            String postalCode
        }

        Context Coverage {
            Money limit
        }
    """.trimIndent()

    private fun problems(body: String): List<String> {
        myFixture.configureByText(
            "inspect.rules",
            """
            $model

            Rule "Under test" On Policy.policyCd {
                $body
            }
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenUnresolvedIdentifierInspection())
        return myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("[kvr049] Reference ") }
    }

    /**
     * A called name is not a reference to resolve. `isCallHead` excludes it, and Qodana
     * reported that condition as always false, so this pins the real behaviour.
     */
    fun testNativeCallHeadIsNotReported() {
        assertEquals(emptyList<String>(), problems("Assert Round(Policy.policyCd) > 0"))
    }

    /** Same for a name nothing declares: calls are not checked. */
    fun testUnknownCallHeadIsNotReported() {
        assertEquals(emptyList<String>(), problems("Assert Inconnue(Policy.policyCd) > 0"))
    }

    /** The guard must not overreach: an unknown argument is still reported. */
    fun testArgumentOfACallIsStillChecked() {
        assertEquals(
            listOf("[kvr049] Reference 'absent' not found."),
            problems("Assert Round(absent) > 0"),
        )
    }

    fun testUnknownIdentifierIsReported() {
        assertEquals(listOf("[kvr049] Reference 'notAThing' not found."), problems("Assert notAThing > 0"))
    }

    fun testFieldOfTheTargetContextIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert policyCd != null"))
    }

    fun testContextNameIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert AddressInfo != null"))
    }

    fun testExpressionVariablesAreAccepted() {
        assertEquals(
            emptyList<String>(),
            problems("Assert every c in AddressInfo satisfies c != null"),
        )
    }

    fun testSetVariableIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert set t to policyCd return t != null"))
    }

    /** Without types, a chain segment cannot be judged. */
    fun testPathSegmentsAreNeverReported() {
        assertEquals(emptyList<String>(), problems("Assert AddressInfo.whateverThisIs != null"))
    }

    fun testFunctionCallHeadIsLeftToTheOtherInspection() {
        assertEquals(emptyList<String>(), problems("Assert Round(policyCd, 2) != null"))
    }

    /**
     * Filter predicate fields resolve against the filtered element. Found on the real
     * kraken-rules corpus, where it caused all of the inspection's false positives.
     */
    fun testFilterPredicateFieldsAreAccepted() {
        assertEquals(
            emptyList<String>(),
            problems("Assert Count(AddressInfo[postalCode != null]) = 1"),
        )
    }

    /**
     * Filter on an unknown head, typically the dynamic external context: the scope is
     * undetermined, so the inspection abstains.
     */
    fun testFilterOnADynamicHeadIsNotJudged() {
        assertEquals(
            emptyList<String>(),
            problems("Assert IsEmpty(context.additional.vehicles[model = policyCd])"),
        )
    }

    /** `context` lives in the engine's global scope and is never declared in the DSL. */
    fun testExternalContextRootIsAccepted() {
        assertEquals(emptyList<String>(), problems("Assert context != null"))
    }

    /** Without a resolvable `On` target there is no reference scope. */
    fun testRuleWithUnknownTargetIsNotJudged() {
        myFixture.configureByText(
            "unknown-target.rules",
            """
            $model

            Rule "No such context" On NotAContext.field {
                Assert anything > 0
            }
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenUnresolvedIdentifierInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("[kvr049] Reference ") }
        assertEquals(emptyList<String>(), reported)
    }

    /**
     * Several visible files can declare the same context, and the field may exist in only
     * one of them. Checking just one, by file order, reported valid code.
     */
    fun testFieldIsFoundAcrossHomonymousContextDeclarations() {
        myFixture.addFileToProject(
            "other-policy.rules",
            """
            Context Policy {
                String somethingElse
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "real-policy.rules",
            """
            Context Policy {
                Date effectiveDate
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "rule.rules",
            """
            Rule "Effective date past" On Policy.effectiveDate {
                Assert effectiveDate < Today()
            }
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenUnresolvedIdentifierInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("[kvr049] Reference ") }
        assertEquals(
            "The field only exists in one of the same-named declarations",
            emptyList<String>(),
            reported,
        )
    }

    fun testFunctionParametersAreAccepted() {
        myFixture.configureByText(
            "function.rules",
            """
            $model

            Function Postal(AddressInfo info) : String {
                info.postalCode
            }
            """.trimIndent(),
        )
        myFixture.enableInspections(KrakenUnresolvedIdentifierInspection())
        val reported = myFixture.doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("[kvr049] Reference ") }
        assertEquals("A function parameter is in scope", emptyList<String>(), reported)
    }
}
