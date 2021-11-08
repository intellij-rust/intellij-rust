/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsVariantDiscriminant
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInteger

/**
 * ```
 * enum Foo {
 *     FooVariant = 1u16 // Error: expected `isize`, found `u16`
 * }
 * ```
 *
 * =>
 *
 * ```
 * #[repr(u16)]
 * enum Foo {
 *     FooVariant = 1u16
 * }
 * ```
 */
class ChangeReprAttributeFix(
    element: RsElement,
    enumName: String,
    private val actualTy: String
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    private val _text: String = run {
        "Change representation of enum `$enumName` to `#[repr($actualTy)]`"
    }

    override fun getText(): String = _text
    override fun getFamilyName(): String = "Change `repr` attribute"

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val owner = findEnumOwner(startElement) as? RsDocAndAttributeOwner ?: return
        val reprAttributes = owner.queryAttributes.reprAttributes.toList()
        val newOuterAttribute = RsPsiFactory(project).createOuterAttr("repr($actualTy)")

        when (reprAttributes.size) {
            // create the repr attribute
            0 -> owner.addBefore(newOuterAttribute, owner.firstChild)
            // replace the repr attribute
            1 -> reprAttributes.single().replace(newOuterAttribute.metaItem)
            // multiple #[repr(...)] attributes are disallowed by "conflicting_repr_hints" hard lint
            else -> return
        }
    }

    companion object {
        private fun findEnumOwner(element: PsiElement): RsEnumItem? {
            return if (element is RsExpr && element.context is RsVariantDiscriminant) {
                element.contextStrict()
            } else {
                null
            }
        }

        fun createIfCompatible(element: RsElement, actualTy: Ty): ChangeReprAttributeFix? {
            if (element.containingCrate.origin != PackageOrigin.WORKSPACE) return null
            if (actualTy !is TyInteger) return null
            val enumOwner = findEnumOwner(element) ?: return null
            val enumName = enumOwner.name ?: ""
            return ChangeReprAttributeFix(element, enumName, actualTy.name)
        }
    }
}
