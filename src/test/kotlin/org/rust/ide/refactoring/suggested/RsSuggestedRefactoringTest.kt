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
import com.intellij.refactoring.suggested.*
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertNotEquals
import org.rust.RsTestBase

abstract class RsSuggestedRefactoringTestBase : RsTestBase() {
    protected fun doUnavailableTest(
        @Language("Rust") initialText: String,
        editingAction: () -> Unit
    ) {
        InlineFile(initialText).withCaret()

        myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
        executeEditingAction(editingAction)

        val intention = suggestedRefactoringIntention()
        assertNull("Refactoring must not be available", intention)
    }

    protected fun doTestRename(
        @Language("Rust") initialText: String,
        @Language("Rust") textAfterRefactoring: String,
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

    protected fun doTestChangeSignature(
        @Language("Rust") initialText: String,
        @Language("Rust") textAfterRefactoring: String,
        usagesName: String,
        editingAction: () -> Unit,
        expectedPresentation: String? = null
    ) {
        doTest(
            replaceCaretMarker(initialText).trimIndent(),
            RefactoringBundle.message("suggested.refactoring.change.signature.intention.text", usagesName),
            replaceCaretMarker(textAfterRefactoring).trimIndent(),
            editingAction,
            checkPresentation = {
                if (expectedPresentation != null) {
                    val state = SuggestedRefactoringProviderImpl.getInstance(project).state!!
                        .let { it.refactoringSupport.availability.refineSignaturesWithResolve(it) }
                    assertEquals(SuggestedRefactoringState.ErrorLevel.NO_ERRORS, state.errorLevel)
                    assertNotEquals(state.oldSignature, state.newSignature)
                    val refactoringSupport = state.refactoringSupport
                    val data = refactoringSupport.availability.detectAvailableRefactoring(state) as SuggestedChangeSignatureData
                    val model = refactoringSupport.ui.buildSignatureChangePresentation(data.oldSignature, data.newSignature)

                    val text = model.dump().trim()
                    assertEquals(expectedPresentation, text)
                }
            }
        )
    }

    private fun doTest(
        initialText: String,
        actionName: String,
        textAfterRefactoring: String,
        action: () -> Unit,
        checkPresentation: () -> Unit = {}
    ) {
        InlineFile(initialText).withCaret()

        myFixture.testHighlighting(false, false, false, myFixture.file.virtualFile)
        executeEditingAction(action)

        val intention = suggestedRefactoringIntention()
        assertNotNull("No refactoring available", intention)
        assertEquals("Action name", actionName, intention!!.text)

        checkPresentation()

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

    private fun executeEditingAction(
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

private fun SignatureChangePresentationModel.dump(): String {
    return buildString {
        append("Old:\n")
        append(oldSignature, ident = "  ")
        append("New:\n")
        append(newSignature, ident = "  ")
    }
}
private fun StringBuilder.append(fragments: List<SignatureChangePresentationModel.TextFragment>, ident: String) {
    for (fragment in fragments) {
        append(ident)

        val properties = mutableListOf<String>()

        when (fragment.effect) {
            SignatureChangePresentationModel.Effect.None -> {}
            SignatureChangePresentationModel.Effect.Added -> properties.add("added")
            SignatureChangePresentationModel.Effect.Removed -> properties.add("removed")
            SignatureChangePresentationModel.Effect.Modified -> properties.add("modified")
            SignatureChangePresentationModel.Effect.Moved -> properties.add("moved")
        }

        when (fragment) {
            is SignatureChangePresentationModel.TextFragment.Leaf -> append("\'${fragment.text}\'")
            is SignatureChangePresentationModel.TextFragment.Group -> append("Group")
            is SignatureChangePresentationModel.TextFragment.LineBreak -> append("LineBreak(\'${fragment.spaceInHorizontalMode}\', ${fragment.indentAfter})")
        }

        if (properties.isNotEmpty()) {
            append(" (")
            properties.joinTo(this, separator = ", ")
            append(")")
        }

        if (fragment is SignatureChangePresentationModel.TextFragment.Group) {
            append(":")
        }

        append("\n")

        if (fragment is SignatureChangePresentationModel.TextFragment.Group) {
            append(fragment.children, "$ident  ")
        }
    }
}
