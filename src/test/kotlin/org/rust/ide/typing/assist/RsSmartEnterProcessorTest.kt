/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors
import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import org.rust.RsTestBase
import org.rust.lang.RsLanguage

/**
 * Unit tests for [RsSmartEnterProcessor]
 */
class RsSmartEnterProcessorTest : RsTestBase() {

    override val dataPath = "org/rust/ide/typing/assist/fixtures"

    fun getSmartProcessors(language: Language) = SmartEnterProcessors.INSTANCE.forKey(language)

    private fun doTest() {
        myFixture.configureByFile(fileName)
        val processors = getSmartProcessors(RsLanguage)

        WriteCommandAction.writeCommandAction(project).run<Throwable> {
            val editor = myFixture.editor
            for (processor in processors) {
                processor.process(project, editor, myFixture.file)
            }
        }

        myFixture.checkResultByFile(fileName.replace(".rs", "_after.rs"), true)
    }

    fun `test fix simple method call`() = doTest()
    fun `test fix nested method call`() = doTest()
    fun `test fix method call with string literal`() = doTest()
    fun `test fix method call multiple lines`() = doTest()
    fun `test fix whitespace and semicolon`() = doTest()
    fun `test fix semicolon after declaration`() = doTest()
    fun `test fix declaration with call`() = doTest()
    fun `test fix match in let`() = doTest()
    fun `test fix call in stmt`() = doTest()
}
