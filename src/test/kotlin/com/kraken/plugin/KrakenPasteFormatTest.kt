package com.kraken.plugin

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.StringSelection

/** Pasting into a `.rules` file must keep the pasted text's indentation. */
class KrakenPasteFormatTest : BasePlatformTestCase() {

    private val pastedRule = """
        Rule "Policy code is mandatory" On Policy.policyCd {
            Set Mandatory
            Error "code" : "message"
        }
    """.trimIndent()

    private fun doPaste(reformat: Int): String {
        val settings = CodeInsightSettings.getInstance()
        val old = settings.REFORMAT_ON_PASTE
        settings.REFORMAT_ON_PASTE = reformat
        disposeOnTearDown(com.intellij.openapi.Disposable { settings.REFORMAT_ON_PASTE = old })
        myFixture.configureByText(
            "test.rules",
            """
            Context Policy {
                String policyCd
            }

            <caret>
            """.trimIndent(),
        )
        CopyPasteManager.getInstance().setContents(StringSelection(pastedRule))
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
        return myFixture.editor.document.text
    }

    fun testPasteWithIndentBlock() {
        val text = doPaste(CodeInsightSettings.INDENT_BLOCK)
        assertTrue(
            "Paste (indent block) mangled the rule body:\n$text",
            text.contains("    Set Mandatory") && text.contains("    Error \"code\" : \"message\""),
        )
    }

    fun testPasteWithReformatBlock() {
        val text = doPaste(CodeInsightSettings.REFORMAT_BLOCK)
        assertTrue(
            "Paste (reformat block) mangled the rule body:\n$text",
            text.contains("    Set Mandatory") && text.contains("    Error \"code\" : \"message\""),
        )
    }

    fun testPasteInsideRuleBody() {
        val settings = CodeInsightSettings.getInstance()
        val old = settings.REFORMAT_ON_PASTE
        settings.REFORMAT_ON_PASTE = CodeInsightSettings.INDENT_BLOCK
        disposeOnTearDown(com.intellij.openapi.Disposable { settings.REFORMAT_ON_PASTE = old })
        myFixture.configureByText(
            "test.rules",
            """
            Rule "r" On Policy.policyCd {
                Set Mandatory
                <caret>
            }
            """.trimIndent(),
        )
        CopyPasteManager.getInstance().setContents(
            StringSelection("Error \"code\" : \"message\""),
        )
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
        val text = myFixture.editor.document.text
        assertTrue(
            "Paste inside rule body lost indentation:\n$text",
            text.contains("    Error \"code\" : \"message\""),
        )
    }

    fun testReformatIsIdempotentOnComplexFile() {
        val wellFormatted = """
            Namespace Policy

            Contexts {
                Context Policy {
                    String policyCd
                    Child AddressInfo
                }
            }

            Rule "complex" On Policy.policyCd {
                When every c in coverages satisfies c.limit > 100
                Assert if state = "CA"
                    then policyCd != null
                    else true
                Error "code" : "message"
            }

            EntryPoints {
                EntryPoint "Validation" {
                    "complex",
                    EntryPoint "Other"
                }
            }
        """.trimIndent()
        myFixture.configureByText("test.rules", wellFormatted)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project)
                .reformat(myFixture.file)
        }
        assertEquals(wellFormatted, myFixture.editor.document.text)
    }

    fun testReformatIsIdempotentOnWellFormattedFile() {
        val wellFormatted = """
            Context Policy {
                String policyCd
            }

            Rule "Policy code is mandatory" On Policy.policyCd {
                Set Mandatory
                Error "code" : "message"
            }
        """.trimIndent()
        myFixture.configureByText("test.rules", wellFormatted)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project)
                .reformat(myFixture.file)
        }
        assertEquals(wellFormatted, myFixture.editor.document.text)
    }
}
