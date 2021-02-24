/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.*
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.psi.ext.isReferenceToConstant

class RsSuggestedRefactoringSupport : SuggestedRefactoringSupport {
    override val availability: SuggestedRefactoringAvailability
        get() = RsSuggestedRefactoringAvailability(this)
    override val execution: SuggestedRefactoringExecution
        get() = SuggestedRefactoringExecution.RenameOnly(this)
    override val stateChanges: SuggestedRefactoringStateChanges
        get() = SuggestedRefactoringStateChanges.RenameOnly(this)
    override val ui: SuggestedRefactoringUI
        get() = SuggestedRefactoringUI.RenameOnly

    override fun importsRange(psiFile: PsiFile): TextRange? = null

    override fun isDeclaration(psiElement: PsiElement): Boolean = when (psiElement) {
        // May return true for const pat binding since we can't distinguish them
        // without name resolution that forbidden here.
        // Refactoring for constants is suppressed by `RsSuggestedRefactoringAvailability`
        is RsPatBinding -> psiElement.parent is RsPatIdent
        is RsNameIdentifierOwner -> true
        else -> false
    }

    override fun isIdentifierPart(c: Char): Boolean = Character.isUnicodeIdentifierStart(c)
    override fun isIdentifierStart(c: Char): Boolean = Character.isUnicodeIdentifierPart(c)

    override fun nameRange(declaration: PsiElement): TextRange? = getRange(declaration)
    override fun signatureRange(declaration: PsiElement): TextRange? = getRange(declaration)

    private fun getRange(declaration: PsiElement): TextRange? =
        (declaration as? RsNameIdentifierOwner)?.nameIdentifier?.textRange
}

private class RsSuggestedRefactoringAvailability(
    refactoringSupport: SuggestedRefactoringSupport
) : SuggestedRefactoringAvailability.RenameOnly(refactoringSupport) {

    override fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean {
        if (state.declaration !is RsPatBinding) return false
        val restoredDeclaration = state.restoredDeclarationCopy() as? RsPatBinding ?: return false
        return restoredDeclaration.isReferenceToConstant
    }
}
