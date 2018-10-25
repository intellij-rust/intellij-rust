/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.isMovesByDefault
import org.rust.lang.core.types.type

class DeriveCopyFix(element: RsElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = name
    override fun getText(): String = "Derive Copy trait"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val pathExpr = startElement as? RsPathExpr ?: return
        val type = pathExpr.type as? TyAdt ?: return
        val item = type.item

        val implLookup = ImplLookup.relativeTo(item)
        val isCloneImplemented = implLookup.isClone(type)

        val psiFactory = RsPsiFactory(project)
        val existingDeriveAttr = item.findOuterAttr("derive")

        if (existingDeriveAttr != null) {
            updateDeriveAttr(psiFactory, existingDeriveAttr, isCloneImplemented)
        } else {
            createDeriveAttr(psiFactory, item, isCloneImplemented)
        }
    }

    private fun updateDeriveAttr(psiFactory: RsPsiFactory, deriveAttr: RsOuterAttr, isCloneImplemented: Boolean) {
        val oldAttrText = deriveAttr.text
        val newAttrText = buildString {
            append(oldAttrText.substringBeforeLast(")"))
            if (isCloneImplemented) append(", Copy)") else append(", Clone, Copy)")
        }

        val newDeriveAttr = psiFactory.createOuterAttr(newAttrText)
        deriveAttr.replace(newDeriveAttr)
    }

    private fun createDeriveAttr(psiFactory: RsPsiFactory, item: RsStructOrEnumItemElement, isCloneImplemented: Boolean) {
        val keyword = item.firstKeyword!!
        val newAttrText = if (isCloneImplemented) "derive(Copy)" else "derive(Clone, Copy)"
        val newDeriveAttr = psiFactory.createOuterAttr(newAttrText)

        item.addBefore(newDeriveAttr, keyword)
    }

    companion object {
        fun createIfCompatible(element: RsElement): DeriveCopyFix? {
            val pathExpr = element as? RsPathExpr ?: return null
            val item = (pathExpr.type as? TyAdt)?.item ?: return null
            if (item.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return null

            val implLookup = ImplLookup.relativeTo(element)

            when (item) {
                is RsStructItem -> {
                    val fieldTypes = item.fieldTypes
                    if (fieldTypes.any { it.isMovesByDefault(implLookup) }) return null
                }
                is RsEnumItem -> {
                    val enumVariants = item.enumBody?.enumVariantList ?: return null
                    for (variant in enumVariants) {
                        if (variant.fieldTypes.any { it.isMovesByDefault(implLookup) }) return null
                    }
                }
            }

            return DeriveCopyFix(pathExpr)
        }
    }
}
