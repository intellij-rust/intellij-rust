/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.rust.RsBundle
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.moveCaretToOffset
import kotlin.math.max
import kotlin.math.min

class RsDbgUsageInspection: RsLocalInspectionTool() {
    override fun getDisplayName(): String = RsBundle.message("dbg.usage")

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor {
        return object : RsWithMacrosInspectionVisitor() {
            override fun visitMacroExpr(o: RsMacroExpr) {
                val macroCall = o.macroCall
                if (macroCall.macroName != "dbg") return
                val resolvedMacro = macroCall.path.reference?.resolve()
                if (resolvedMacro != null && resolvedMacro.containingCrate.origin != PackageOrigin.STDLIB) return
                if (!PsiModificationUtil.canReplace(macroCall)) return
                holder.registerProblem(macroCall, RsBundle.message("dbg.usage"), RsRemoveDbgQuickFix(macroCall, isOnTheFly))
            }
        }
    }
}

private class RsRemoveDbgQuickFix(macroCall: RsMacroCall, val isOnTheFly: Boolean): RsQuickFixBase<RsMacroCall>(macroCall) {
    override fun getFamilyName(): String = RsBundle.message("intention.name.remove.dbg")

    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsMacroCall) {
        val expr = element.exprMacroArgument?.expr ?: return
        var cursorOffsetToExpr = if (editor != null) max(0, editor.caretModel.offset - expr.startOffset) else -1
        val parent = element.parent.parent
        val newExpr = if (expr is RsBinaryExpr && (parent is RsBinaryExpr || parent is RsDotExpr)) {
            if (editor != null) {
                cursorOffsetToExpr += 1
            }
            element.replaceWithExpr(RsPsiFactory(project).createExpression("(${expr.text})"))
        } else {
            element.replaceWithExpr(expr)
        }
        if (editor != null && isOnTheFly) {
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            editor.moveCaretToOffset(newExpr, min(newExpr.startOffset + cursorOffsetToExpr, newExpr.endOffset))
        }
    }

}
