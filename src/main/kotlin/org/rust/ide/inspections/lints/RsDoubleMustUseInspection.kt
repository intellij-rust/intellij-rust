/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsAttr
import org.rust.lang.core.psi.ext.findFirstMetaItem
import org.rust.lang.core.psi.ext.normReturnType
import org.rust.lang.core.types.ty.TyAdt

private class FixRemoveMustUseAttr(
    element: PsiElement,
) : RsQuickFixBase<PsiElement>(element) {
    override fun getText(): String = RsBundle.message("inspection.DoubleMustUse.FixRemoveMustUseAttr.name")
    override fun getFamilyName(): String = text
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        element.delete()
    }
}

/** Analogue of Clippy's double_must_use. */
class RsDoubleMustUseInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.DoubleMustUse

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        override fun visitFunction2(o: RsFunction) {
            val mustUseAttrName = "must_use"
            val metaItemOnFunc = o.findFirstMetaItem(mustUseAttrName)
            val type = o.normReturnType as? TyAdt
            val attrType = type?.item?.findFirstMetaItem(mustUseAttrName)
            if (metaItemOnFunc != null && attrType != null) {
                val description = RsBundle.message("inspection.DoubleMustUse.description")
                val highlighting = RsLintHighlightingType.WEAK_WARNING
                val attr = metaItemOnFunc.parent.takeIf { it is RsAttr } ?: metaItemOnFunc
                val fixes = if (attr is RsAttr) listOf(FixRemoveMustUseAttr(attr)) else emptyList()
                holder.registerLintProblem(attr, description, highlighting, fixes)
            }
        }
    }
}
