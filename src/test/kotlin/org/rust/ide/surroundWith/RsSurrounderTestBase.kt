/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.LanguageSurrounders
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction
import org.intellij.lang.annotations.Language
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.RsTestBase

abstract class RsSurrounderTestBase(private val surrounder: Surrounder) : RsTestBase() {
    protected fun doTest(@Language("Rust") before: String,
                         @Language("Rust") after: String,
                         checkSyntaxErrors: Boolean = true) {
        myFixture.configureByText(RsFileType, before)

        checkApplicability(fileName, true)
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
            SurroundWithHandler.invoke(myFixture.project, myFixture.editor, myFixture.file, surrounder)
        }

        if (checkSyntaxErrors) myFixture.checkHighlighting(false, false, false)
        myFixture.checkResult(after)
    }

    protected fun doTestNotApplicable(@Language("Rust") before: String) {
        myFixture.configureByText(RsFileType, before)
        checkApplicability(before, false)
    }

    private fun checkApplicability(testCase: String, isApplicable: Boolean) {
        val descriptor = LanguageSurrounders.INSTANCE.allForLanguage(RsLanguage)
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

        check(surrounder.isApplicable(elements) == isApplicable) {
            "surrounder ${if (isApplicable) "should" else "shouldn't"} be applicable to given selection:\n\n" +
                "$testCase\n" +
                "Elements: ${elements.toList()}"
        }
    }
}
