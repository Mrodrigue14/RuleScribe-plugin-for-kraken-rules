package com.kraken.plugin

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.lang.KrakenFile
import com.kraken.plugin.psi.KrakenEntryPointDecl
import com.kraken.plugin.psi.KrakenEpRef
import com.kraken.plugin.psi.KrakenRuleDecl
import com.kraken.plugin.psi.KrakenRuleRef

abstract class KrakenMoveTestCase : BasePlatformTestCase() {

    protected fun file(name: String, text: String): KrakenFile = myFixture.addFileToProject(name, text) as KrakenFile

    protected fun ruleIn(file: KrakenFile, name: String): KrakenRuleDecl = PsiTreeUtil.findChildrenOfType(file, KrakenRuleDecl::class.java).first { it.name == name }

    protected fun epIn(file: KrakenFile, name: String): KrakenEntryPointDecl = PsiTreeUtil.findChildrenOfType(file, KrakenEntryPointDecl::class.java).first { it.name == name }

    protected fun ruleRefIn(file: KrakenFile, name: String): KrakenRuleRef = PsiTreeUtil.findChildrenOfType(file, KrakenRuleRef::class.java).first { it.ruleName == name }

    protected fun epRefIn(file: KrakenFile, name: String): KrakenEpRef = PsiTreeUtil.findChildrenOfType(file, KrakenEpRef::class.java).first { it.entryPointName == name }

    /** The move goes through the document, so both files must still parse. */
    protected fun assertParses(vararg files: KrakenFile) {
        for (f in files) {
            val errors = PsiTreeUtil.findChildrenOfType(f, PsiErrorElement::class.java)
            assertEquals(
                "${f.name} no longer parses:\n${f.text}",
                emptyList<String>(),
                errors.map { it.errorDescription },
            )
        }
    }
}
