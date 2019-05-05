/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.psi.ext.operatorType


/**
 * Fix that converts the given reference to owned value.
 * @param argEl An element, that represents a reference from which the first
 * symbol '&' must be removed.
 * @param fixName A name to use for the fix instead of the default one to better fit the inspection.
 */
class RemoveRefFix(
    argEl: RsExpr,
    val fixName: String = "Change reference to owned value"
) : LocalQuickFixOnPsiElement(argEl) {
    override fun getText() = fixName
    override fun getFamilyName() = "Change reference to owned value"

    override fun invoke(project: Project, file: PsiFile, argEl: PsiElement, endElement: PsiElement) {
        // If the reference is a mut one (&mut X) we want to remove the `mut` part too as `mut X` is either:
        // - not valid Rust code (e.g.: `std::mem::drop(mut x)`)
        // - or redundant (e.g.: `fn foo(mut self) {}` is exactly the same as `fn foo(self) {}`)
        if (argEl is RsUnaryExpr && argEl.operatorType in listOf(UnaryOperator.REF, UnaryOperator.REF_MUT)) {
            argEl.expr?.let { argEl.replace(it) }
        }
    }
}
