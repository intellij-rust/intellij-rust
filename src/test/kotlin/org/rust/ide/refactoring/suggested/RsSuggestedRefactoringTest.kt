/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.RefactoringBundle
import org.rust.RsTestBase

abstract class RsSuggestedRefactoringTestBase : RsTestBase() {
    protected fun doUnavailableTest(
        initialText: String,
        editingAction: () -> Unit
    ) {
        InlineFile(initialText).withCaret()

        myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
        executeEditingAction(editingAction)

        val intention = suggestedRefactoringIntention()
        assertNull("Refactoring must not be available", intention)
    }

    protected fun doTestRename(
        initialText: String,
        textAfterRefactoring: String,
        oldName: String,
        newName: String,
        editingAction: () -> Unit
    ) {
        doTest(
            replaceCaretMarker(initialText).trimIndent(),
            RefactoringBundle.message("suggested.refactoring.rename.intention.text", oldName, newName),
            replaceCaretMarker(textAfterRefactoring).trimIndent(),
            editingAction
        )
    }

    private fun doTest(
        initialText: String,
        actionName: String,
        textAfterRefactoring: String,
        action: () -> Unit
    ) {
        InlineFile(initialText).withCaret()

        myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
        executeEditingAction(action)

        val intention = suggestedRefactoringIntention()
        assertNotNull("No refactoring available", intention)
        assertEquals("Action name", actionName, intention!!.text)

        val editor = myFixture.editor
        executeCommand(project) {
            intention.invoke(project, editor, myFixture.file)

            runWriteAction {
                PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
            }
        }

        val index = textAfterRefactoring.indexOf("<caret>")
        if (index >= 0) {
            val text = textAfterRefactoring.substring(0, index) +
                textAfterRefactoring.substring(index + "<caret>".length)
            assertEquals(text, editor.document.text)

            assertEquals("Caret position", index, editor.caretModel.offset)
        } else {
            assertEquals(textAfterRefactoring, editor.document.text)
        }
    }

    private fun suggestedRefactoringIntention(): IntentionAction? =
        myFixture.availableIntentions.firstOrNull { it.familyName == "Suggested Refactoring" }

    protected fun executeEditingAction(
        action: () -> Unit,
        wrapIntoCommandAndWriteActionAndCommitAll: Boolean = true
    ) {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        if (wrapIntoCommandAndWriteActionAndCommitAll) {
            executeCommand {
                runWriteAction {
                    action()
                    psiDocumentManager.commitAllDocuments()
                    psiDocumentManager.doPostponedOperationsAndUnblockDocument(myFixture.editor.document)
                }
            }
        } else {
            action()
        }

        psiDocumentManager.commitAllDocuments()
    }
}
