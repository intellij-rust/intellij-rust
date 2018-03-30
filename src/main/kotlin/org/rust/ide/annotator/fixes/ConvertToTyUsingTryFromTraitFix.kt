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
import org.rust.ide.presentation.tyToStringWithoutTypeArgs
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.withSubst
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.StdKnownItems
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

/**
 * Base class for converting the given `expr` to the type [ty] using trait [traitName] and its conversion method
 * [methodName]. Note the fix neither try to verify that the [traitName] and [methodName] actually exist, nor check
 * that the [traitName] is actually implemented for [ty].
 */
abstract class ConvertToTyUsingTryTraitFix(
    expr: PsiElement,
    private val ty: Ty,
    private val traitName: String,
    private val methodName: String) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {

    override fun getFamilyName(): String = "Convert to type"

    override fun getText(): String = "Convert to $ty using `$traitName` trait"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsExpr) return
        val rsPsiFactory = RsPsiFactory(project)
        val fromCall = rsPsiFactory.createAssocFunctionCall(tyToStringWithoutTypeArgs(ty), methodName, listOf(startElement))
        addFromCall(rsPsiFactory, startElement, fromCall)
    }

    open fun addFromCall(rsPsiFactory: RsPsiFactory, startElement: RsExpr, fromCall: RsCallExpr) {
        startElement.replace(fromCall)
    }
}

/**
 * Similar to [ConvertToTyUsingTryTraitFix], but also "unwraps" the result with `unwrap()` or `?`.
 */
abstract class ConvertToTyUsingTryTraitAndUnpackFix(
    expr: PsiElement,
    ty: Ty,
    private val errTy: Ty,
    traitName: String,
    methodName: String) : ConvertToTyUsingTryTraitFix(expr, ty, traitName, methodName) {

    override fun addFromCall(rsPsiFactory: RsPsiFactory, startElement: RsExpr, fromCall: RsCallExpr) {
        val parentFnRetTy = findParentFnOrLambdaRetTy(startElement)
        when {
            parentFnRetTy != null && isFnRetTyResultAndMatchErrTy(startElement, parentFnRetTy) ->
                startElement.replace(rsPsiFactory.createTryExpression(fromCall))
            else -> startElement.replace(rsPsiFactory.createNoArgsMethodCall(fromCall, "unwrap"))
        }
    }

    private fun findParentFnOrLambdaRetTy(element: RsExpr): Ty? =
        findParentFunctionOrLambdaRsRetType(element)?.typeReference?.type

    private fun findParentFunctionOrLambdaRsRetType(element: RsExpr): RsRetType? {
        var parent = element.parent
        while (parent != null) {
            when (parent) {
                is RsFunction -> return parent.retType
                is RsLambdaExpr -> return parent.retType
                else -> parent = parent.parent
            }
        }
        return null
    }

    private fun isFnRetTyResultAndMatchErrTy(element: RsExpr, fnRetTy: Ty): Boolean {
        val items = StdKnownItems.relativeTo(element)
        val lookup = ImplLookup(element.project, items)
        return fnRetTy is TyAdt && fnRetTy.item == items.findResultItem()
            && lookup.select(TraitRef(fnRetTy.typeArguments.get(1), (items.findFromTrait()
            ?: return false).withSubst(errTy))).ok() != null
    }
}

/**
 * For the given `expr` converts it to the type `Result<ty, _>` with `ty::try_from(expr)`.
 */
class ConvertToTyUsingTryFromTraitFix(expr: PsiElement, ty: Ty) :
    ConvertToTyUsingTryTraitFix(expr, ty, "TryFrom", "try_from")

/**
 * For the given `expr` converts it to the type [ty] with `ty::try_from(expr).unwrap()` or `ty::try_from(expr)?` if
 * possible.
 */
class ConvertToTyUsingTryFromTraitAndUnpackFix(expr: PsiElement, ty: Ty, errTy: Ty) :
    ConvertToTyUsingTryTraitAndUnpackFix(expr, ty, errTy, "TryFrom", "try_from")
