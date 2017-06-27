/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors
import com.intellij.lang.Language
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import org.rust.lang.RsLanguage
import org.rust.lang.RsTestBase

/**
 * Unit tests for [RsSmartEnterProcessor]
 */
class RsSmartEnterProcessorTest : RsTestBase() {

    override val dataPath = "org/rust/ide/typing/assist/fixtures"

    fun getSmartProcessors(language: Language) = SmartEnterProcessors.INSTANCE.forKey(language)

    private fun doTest() {
        myFixture.configureByFile("$fileName")
        val processors = getSmartProcessors(RsLanguage)

        val writeCommand = object : WriteCommandAction<RsSmartEnterProcessor>(project) {
            override fun run(result: Result<RsSmartEnterProcessor>) {
                val editor = myFixture.editor
                for (processor in processors) {
                    processor.process(project, editor, myFixture.file)
                }
            }
        }
        writeCommand.execute()

        myFixture.checkResultByFile("$fileName".replace(".rs", "_after.rs"), true)
    }

    fun testFixSimpleMethodCall() = doTest()
    fun testFixNestedMethodCall() = doTest()
    fun testFixMethodCallWithStringLiteral() = doTest()
    fun testFixMethodCallMultipleLines() = doTest()
    fun testFixWhitespaceAndSemicolon() = doTest()
    fun testFixSemicolonAfterDeclaration() = doTest()
    fun testFixDeclarationWithCall() = doTest()
    fun testFixMatchInLet() = doTest()
    fun testFixCallInStmt() = doTest()
}
