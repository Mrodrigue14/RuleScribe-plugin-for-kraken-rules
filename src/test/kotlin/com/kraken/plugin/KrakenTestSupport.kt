package com.kraken.plugin

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.kraken.plugin.psi.KrakenRefExpr
import org.junit.Assert

/** Descriptions of the syntax errors in [file]; empty when it parses. */
fun parseErrors(file: PsiFile): List<String> = allOf<PsiErrorElement>(file).map { it.errorDescription }

/** Opens [source] and returns its syntax errors. */
fun CodeInsightTestFixture.parseErrorsOf(source: String): List<String> = parseErrors(configureByText("test.rules", source))

/** Every element of type [T] under [root]. */
inline fun <reified T : PsiElement> allOf(root: PsiElement): List<T> = PsiTreeUtil.findChildrenOfType(root, T::class.java).toList()

/** Descriptions of what the enabled inspections report on the current file. */
fun CodeInsightTestFixture.problemDescriptions(): List<String> = doHighlighting().mapNotNull { it.description }

/** Fails unless a reported problem starts with [prefix], listing what was reported. */
fun CodeInsightTestFixture.assertReported(prefix: String) {
    val problems = problemDescriptions()
    Assert.assertTrue("Expected a problem starting with '$prefix', got: $problems", problems.any { it.startsWith(prefix) })
}

/** Fails if a reported problem starts with [prefix]. */
fun CodeInsightTestFixture.assertNotReported(prefix: String) {
    val problems = problemDescriptions()
    Assert.assertFalse("Expected no problem starting with '$prefix', got: $problems", problems.any { it.startsWith(prefix) })
}

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
        return myFixture.problemDescriptions()
    }
}
