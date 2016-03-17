package org.rust.ide.editor

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors
import com.intellij.lang.Language
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import org.rust.lang.RustLanguage
import org.rust.lang.RustTestCaseBase

class RustSmartEnterTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/editor/fixtures/smartenter"

    fun getSmartProcessors(language: Language) = SmartEnterProcessors.INSTANCE.forKey(language)

    fun doTest() {
        myFixture.configureByFile("$fileName")
        val processors = getSmartProcessors(RustLanguage);

        val writeCommand = object : WriteCommandAction<RustSmartEnterProcessor>(project) {
            override fun run(result: Result<RustSmartEnterProcessor>) {
                val editor = myFixture.editor;
                for (processor in processors) {
                    processor.process(project, editor, myFixture.file);
                }
            }
        }
        writeCommand.execute()

        myFixture.checkResultByFile("$fileName".replace(".rs", "_after.rs"), true);
    }

    fun testMatchParenthesis() = doTest()

}
