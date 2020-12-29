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
        get() = SuggestedRefactoringAvailability.RenameOnly(this)
    override val execution: SuggestedRefactoringExecution
        get() = SuggestedRefactoringExecution.RenameOnly(this)
    override val stateChanges: SuggestedRefactoringStateChanges
        get() = SuggestedRefactoringStateChanges.RenameOnly(this)
    override val ui: SuggestedRefactoringUI
        get() = SuggestedRefactoringUI.RenameOnly

    override fun importsRange(psiFile: PsiFile): TextRange? = null

    override fun isDeclaration(psiElement: PsiElement): Boolean = when (psiElement) {
        is RsPatBinding -> psiElement.parent is RsPatIdent && !psiElement.isReferenceToConstant
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
