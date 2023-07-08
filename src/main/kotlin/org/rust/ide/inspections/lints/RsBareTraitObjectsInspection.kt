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
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.dyn
import org.rust.lang.core.psi.ext.isAtLeastEdition2018
import org.rust.lang.core.psi.ext.skipParens
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsBareTraitObjectsInspection : RsLintInspection() {
    override fun getLint(element: PsiElement): RsLint = RsLint.BareTraitObjects

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitTypeReference(typeReference: RsTypeReference) {
                if (!typeReference.isAtLeastEdition2018) return

                val traitType = typeReference.skipParens() as? RsTraitType
                val typePath = (typeReference.skipParens() as? RsPathType)?.path
                val isTraitType = traitType != null || typePath?.reference?.deepResolve() is RsTraitItem
                val isSelf = typePath?.cself != null
                val hasDyn = traitType?.dyn != null
                val hasImpl = traitType?.impl != null
                if (!isTraitType || isSelf || hasDyn || hasImpl) return

                RsDiagnostic.TraitObjectWithNoDyn(typeReference, AddDynKeywordFix(typeReference)).addToHolder(holder)
            }
        }

    private class AddDynKeywordFix(element: RsTypeReference) : RsQuickFixBase<RsTypeReference>(element) {
        override fun getText(): String = RsBundle.message("intention.name.add.dyn.keyword.to.trait.object")
        override fun getFamilyName(): String = text

        override fun invoke(project: Project, editor: Editor?, element: RsTypeReference) {
            val typeElement = element.skipParens()
            val traitText = (typeElement as? RsPathType)?.path?.text ?: (typeElement as RsTraitType).text
            val new = RsPsiFactory(project).createDynTraitType(traitText)
            element.replace(new)
        }
    }
}
