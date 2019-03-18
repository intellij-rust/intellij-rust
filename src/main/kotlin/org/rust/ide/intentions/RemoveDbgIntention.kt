/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroExpr
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.psi.ext.startOffset
import kotlin.math.max
import kotlin.math.min

/**
 * Removes the `dbg!` macro
 *
 * ```
 * let a = dbg!(variable);
 * ```
 *
 * to this:
 *
 * ```
 * let a = variable;
 * ```
 */
class RemoveDbgIntention : RsElementBaseIntentionAction<RsMacroExpr>() {

    override fun getText() = "Remove dbg!"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsMacroExpr? {
        val macroExpr = element.ancestorStrict<RsMacroExpr>() ?: return null
        if (macroExpr.macroCall.macroName != "dbg") {
            return null
        }
        return macroExpr
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsMacroExpr) {
        val expr = ctx.macroCall.exprMacroArgument?.expr ?: return
        val cursorOffsetToExpr = max(0, editor.caretModel.offset - expr.startOffset)
        val newExpr = ctx.replace(expr)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        editor.caretModel.moveToOffset(min(newExpr.startOffset + cursorOffsetToExpr, newExpr.endOffset))
    }
}
