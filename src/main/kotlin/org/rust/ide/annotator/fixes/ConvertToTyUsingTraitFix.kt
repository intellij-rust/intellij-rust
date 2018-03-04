/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty

/**
 * For the given `expr` adds method call defined by [methodName]. Note the fix doesn't attempt to check the type
 * of `expr` and so doesn't check if adding the function call will produce a valid expression. The conversion trait
 * [traitName] and resulting type [tyName] are used only for messaging.
 */
abstract class ConvertToTyUsingTraitFix(
    expr: PsiElement,
    private val tyName: String,
    private val traitName: String,
    private val methodName: String) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {

    constructor(expr: PsiElement, ty: Ty, traitName: String, methodName: String) : this(expr, ty.toString(), traitName, methodName)

    override fun getFamilyName(): String = "Convert to type"

    override fun getText(): String = "Convert to $tyName using `$traitName` trait"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsExpr) return
        startElement.replace(RsPsiFactory(project).createNoArgsMethodCall(startElement, methodName))
    }
}

/**
 * For the given `expr` converts it to the borrowed type with `borrow()` method.
 */
class ConvertToBorrowedTyFix(expr: PsiElement, ty: Ty) : ConvertToTyUsingTraitFix(expr, ty, "Borrow", "borrow")

/**
 * For the given `expr` converts it to the borrowed type with `borrow_mut()` method.
 */
class ConvertToBorrowedTyWithMutFix(expr: PsiElement, ty: Ty) : ConvertToTyUsingTraitFix(expr, ty, "BorrowMut", "borrow_mut")

/**
 * For the given `expr` converts it to the borrowed type with `as_mut()` method.
 */
class ConvertToMutTyFix(expr: PsiElement, ty: Ty) : ConvertToTyUsingTraitFix(expr, ty, "AsMut", "as_mut")

/**
 * For the given `expr` converts it to the reference type with `as_ref()` method.
 */
class ConvertToRefTyFix(expr: PsiElement, ty: Ty) : ConvertToTyUsingTraitFix(expr, ty, "AsRef", "as_ref")

/**
 * For the given `expr` converts it to the owned type with `to_owned()` method.
 */
class ConvertToOwnedTyFix(expr: PsiElement, ty: Ty): ConvertToTyUsingTraitFix(expr, ty, "ToOwned", "to_owned")

/**
 * For the given `expr` adds `to_string()` call. Note the fix doesn't attempt to check if adding the function call
 * will produce a valid expression.
 */
class ConvertToStringFix(expr: PsiElement) : ConvertToTyUsingTraitFix(expr, "String", "ToString", "to_string")
