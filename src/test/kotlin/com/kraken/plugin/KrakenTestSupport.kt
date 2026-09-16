package com.kraken.plugin

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.kraken.plugin.psi.KrakenRefExpr

/** Descriptions of the syntax errors in [file]; empty when it parses. */
fun parseErrors(file: PsiFile): List<String> = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).map { it.errorDescription }

/** A test that writes one rule body against a context model rooted at `Policy`. */
abstract class KrakenRuleBodyTestCase : BasePlatformTestCase() {

    /** The contexts the rule under test can see. */
    protected abstract val model: String

    /** Opens a file declaring [model] and `Rule "Under test" On Policy.policyCd { body }`. */
    protected fun configureRule(body: String): PsiFile = myFixture.configureByText(
        "rule.rules",
        """
        $model

        Rule "Under test" On Policy.policyCd {
            $body
        }
        """.trimIndent(),
    )

    /** The first bare identifier named [name]. */
    protected fun refNamed(name: String): KrakenRefExpr = PsiTreeUtil.collectElementsOfType(myFixture.file, KrakenRefExpr::class.java)
        .firstOrNull { it.referenceName == name }
        ?: throw AssertionError("No reference '$name' in the file")

    /** Descriptions of what [inspection] reports on [body]. */
    protected fun problemsIn(body: String, inspection: LocalInspectionTool): List<String> {
        configureRule(body)
        myFixture.enableInspections(inspection)
        return myFixture.doHighlighting().mapNotNull { it.description }
    }
}
