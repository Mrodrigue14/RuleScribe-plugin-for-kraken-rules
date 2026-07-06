package com.kraken.plugin

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.stubs.KrakenRuleNameIndex

class KrakenStubIndexTest : BasePlatformTestCase() {

    fun testRuleNameIsIndexed() {
        myFixture.configureByText(
            "a.rules",
            """
            Rule "Indexed rule" On Policy.state {
                Assert true
            }
            """.trimIndent()
        )
        val keys = StubIndex.getInstance().getAllKeys(KrakenRuleNameIndex.KEY, project)
        assertTrue("Expected 'Indexed rule' among $keys", keys.contains("Indexed rule"))

        val elements = StubIndex.getElements(
            KrakenRuleNameIndex.KEY,
            "Indexed rule",
            project,
            GlobalSearchScope.projectScope(project),
            KrakenRuleDecl::class.java
        )
        assertEquals(1, elements.size)
        assertEquals("Indexed rule", elements.first().name)
    }

    fun testResolutionUsesIndexAcrossFiles() {
        myFixture.addFileToProject(
            "rules.rules",
            """
            Rule "From index" On Policy.x {
                Assert true
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "eps.rules",
            """
            EntryPoint "E" {
                "From<caret> index"
            }
            """.trimIndent()
        )
        val target = myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve()
        assertTrue(target is KrakenRuleDecl)
        assertEquals("rules.rules", (target as KrakenRuleDecl).containingFile.name)
    }
}
