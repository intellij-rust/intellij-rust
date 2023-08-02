/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty

abstract class ConvertToTyUsingTraitFix : ConvertToTyFix {
    constructor(expr: RsExpr, tyName: String, traitName: String) : super(expr, tyName, "`$traitName` trait")
    constructor(expr: RsExpr, ty: Ty, traitName: String) : super(expr, ty, "`$traitName` trait")
}

/**
 * For the given `expr` adds method call defined by [methodName]. Note the fix doesn't attempt to check the type
 * of `expr` and so doesn't check if adding the function call will produce a valid expression. The conversion trait
 * [traitName] and resulting type [tyName] are used only for messaging.
 */
@Suppress("KDocUnresolvedReference")
abstract class ConvertToTyUsingTraitMethodFix : ConvertToTyUsingTraitFix {

    private val methodName: String

    constructor(expr: RsExpr, tyName: String, traitName: String, methodName: String) : super(expr, tyName, traitName) {
        this.methodName = methodName
    }

    constructor(expr: RsExpr, ty: Ty, traitName: String, methodName: String) : super(expr, ty, traitName) {
        this.methodName = methodName
    }

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        element.replace(RsPsiFactory(project).createNoArgsMethodCall(element, methodName))
    }
}

/**
 * For the given `expr` converts it to the borrowed type with `borrow()` method.
 */
class ConvertToBorrowedTyFix(expr: RsExpr, ty: Ty) : ConvertToTyUsingTraitMethodFix(expr, ty, "Borrow", "borrow")

/**
 * For the given `expr` converts it to the borrowed type with `borrow_mut()` method.
 */
class ConvertToBorrowedTyWithMutFix(expr: RsExpr, ty: Ty) : ConvertToTyUsingTraitMethodFix(expr, ty, "BorrowMut", "borrow_mut")

/**
 * For the given `expr` converts it to the borrowed type with `as_mut()` method.
 */
class ConvertToMutTyFix(expr: RsExpr, ty: Ty) : ConvertToTyUsingTraitMethodFix(expr, ty, "AsMut", "as_mut")

/**
 * For the given `expr` converts it to the reference type with `as_ref()` method.
 */
class ConvertToRefTyFix(expr: RsExpr, ty: Ty) : ConvertToTyUsingTraitMethodFix(expr, ty, "AsRef", "as_ref")

/**
 * For the given `expr` converts it to the owned type with `to_owned()` method.
 */
class ConvertToOwnedTyFix(expr: RsExpr, ty: Ty): ConvertToTyUsingTraitMethodFix(expr, ty, "ToOwned", "to_owned")

/**
 * For the given `expr` adds `to_string()` call. Note the fix doesn't attempt to check if adding the function call
 * will produce a valid expression.
 */
class ConvertToStringFix(expr: RsExpr) : ConvertToTyUsingTraitMethodFix(expr, "String", "ToString", "to_string")
