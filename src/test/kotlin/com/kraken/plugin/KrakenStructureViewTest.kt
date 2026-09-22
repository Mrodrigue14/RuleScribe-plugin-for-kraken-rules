package com.kraken.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.structure.KrakenStructureViewModel

class KrakenStructureViewTest : BasePlatformTestCase() {

    fun testListsDeclarationsInFileOrderWithTheirKind() {
        myFixture.configureByText(
            "structure.rules",
            """
            Dimension "state" : String

            Contexts {
                Context Policy {
                    String policyCd
                }
            }

            Function Plan(Policy policy) : String {
                policy.policyCd
            }

            Rules {
                Rule "Code is mandatory" On Policy.policyCd {
                    Set Mandatory
                }
            }

            Rule On Policy.policyCd {
                Set Hidden
            }

            EntryPoint "Validation" {
                "Code is mandatory"
            }
            """.trimIndent(),
        )

        val model = KrakenStructureViewModel(myFixture.file as KrakenFile, null)
        val entries = model.root.children.map { "${it.presentation.presentableText} (${it.presentation.locationString})" }

        assertEquals(
            listOf(
                "state (dimension)",
                "Policy (context)",
                "Plan (function)",
                "Code is mandatory (rule)",
                "Rule (rule)",
                "Validation (entry point)",
            ),
            entries,
        )
        model.dispose()
    }
}
