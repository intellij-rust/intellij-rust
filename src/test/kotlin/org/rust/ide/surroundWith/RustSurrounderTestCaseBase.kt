package org.rust.ide.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.LanguageSurrounders
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustFileType
import org.rust.lang.RustLanguage
import org.rust.lang.RustTestCaseBase
import java.util.*

abstract class RustSurrounderTestCaseBase(val surrounder: Surrounder) : RustTestCaseBase() {
    override val dataPath: String  = ""

    public fun doTest(before: String, after: String) {
        myFixture.configureByText(RustFileType, before)

        checkApplicability(fileName, true)
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            SurroundWithHandler.invoke(myFixture.project, myFixture.editor, myFixture.file, surrounder)
        }

        myFixture.checkResult(after)
    }

    public fun doTestNotApplicable(before: String) {
        myFixture.configureByText(RustFileType, before)
        checkApplicability(before, false)
    }

    private fun checkApplicability(testCase: String, isApplicable: Boolean) {
        val descriptor = LanguageSurrounders.INSTANCE.allForLanguage(RustLanguage)
            .first { descriptor ->
                descriptor.surrounders.any { surrounder ->
                    surrounder.javaClass == this.surrounder.javaClass
                }
            }

        val selectionModer = myFixture.editor.selectionModel
        if (!selectionModer.hasSelection())
            selectionModer.selectLineAtCaret()

        val elements = descriptor.getElementsToSurround(
            myFixture.file, selectionModer.selectionStart, selectionModer.selectionEnd)

        assertThat(surrounder.isApplicable(elements))
            .withFailMessage(
                "surrounder %s be applicable to given selection:\n\n%s\nElements: %s",
                if (isApplicable) "should" else "shouldn't",
                testCase,
                Arrays.toString(elements)
            )
            .isEqualTo(isApplicable)
    }
}
