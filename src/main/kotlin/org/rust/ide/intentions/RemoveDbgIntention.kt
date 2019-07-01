/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.*
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
class RemoveDbgIntention : RsElementBaseIntentionAction<RsMacroCall>() {

    override fun getText() = "Remove dbg!"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsMacroCall? {
        val macroCall = element.ancestorStrict<RsMacroCall>() ?: return null
        if (macroCall.macroName != "dbg") {
            return null
        }
        return macroCall
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsMacroCall) {
        val expr = ctx.exprMacroArgument?.expr ?: return
        val cursorOffsetToExpr = max(0, editor.caretModel.offset - expr.startOffset)
        val newExpr = ctx.replaceWithExpr(expr)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        editor.caretModel.moveToOffset(min(newExpr.startOffset + cursorOffsetToExpr, newExpr.endOffset))
    }
}
