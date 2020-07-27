/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.import.AutoImportFix
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.import.import
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.TraitImplSource
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

class ConvertMethodCallToUFCSIntention : RsElementBaseIntentionAction<ConvertMethodCallToUFCSIntention.Context>() {
    override fun getText() = "Convert to UFCS"
    override fun getFamilyName() = text

    override fun findApplicableContext(
        project: Project,
        editor: Editor,
        element: PsiElement
    ): Context? {
        val methodCall = element.parent as? RsMethodCall ?: return null
        val function = methodCall.reference.resolve() as? RsFunction ?: return null
        val methodVariants = methodCall.inference?.getResolvedMethod(methodCall).orEmpty()
        if (methodVariants.isEmpty()) return null
        return Context(methodCall, function, methodVariants)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val methodCall = ctx.methodCall
        val function = ctx.function
        val functionName = function.name ?: return

        val factory = RsPsiFactory(project)

        val selfType = getSelfType(function) ?: return
        val receiver = methodCall.receiver
        val prefix = getSelfArgumentPrefix(selfType, receiver)
        val selfArgument = factory.createExpression("$prefix${receiver.text}")

        val arguments = listOf(selfArgument) + methodCall.valueArgumentList.exprList
        val ownerName = getOwnerName(ctx.methodVariants)
        val ufcs = factory.createAssocFunctionCall(ownerName, functionName, arguments)

        val parentDot = methodCall.parentDotExpr
        val inserted = parentDot.replace(ufcs) as RsCallExpr
        val path = (inserted.expr as? RsPathExpr)?.path ?: return

        val importCtx = AutoImportFix.findApplicableContext(project, path)
        importCtx?.candidates?.firstOrNull()?.import(inserted)
    }

    data class Context(
        val methodCall: RsMethodCall,
        val function: RsFunction,
        val methodVariants: List<MethodResolveVariant>
    )
}

private fun getOwnerName(methodVariants: List<MethodResolveVariant>): String {
    val variant = methodVariants.minBy {
        if (it.source is TraitImplSource.ExplicitImpl) {
            0
        } else {
            1
        }
    } ?: error("Method not resolved to any variant")

    fun renderType(ty: Ty): String = ty.renderInsertionSafe(
        includeTypeArguments = false,
        includeLifetimeArguments = false
    )

    return when (val type = variant.selfTy) {
        is TyAnon, is TyTraitObject -> (variant.source.value as? RsTraitItem)?.name ?: renderType(type)
        else -> renderType(type)
    }
}

private enum class SelfType {
    Move,
    Ref,
    RefMut
}

private fun getSelfType(function: RsFunction): SelfType? {
    val self = function.selfParameter ?: return null
    val ref = self.isRef

    return when {
        !ref -> SelfType.Move
        self.mutability == Mutability.MUTABLE -> SelfType.RefMut
        else -> SelfType.Ref
    }
}

private fun getSelfArgumentPrefix(selfType: SelfType, receiver: RsExpr): String {
    val type = receiver.type
    return when (selfType) {
        SelfType.Move -> ""
        SelfType.Ref -> {
            if (type is TyReference) {
                ""
            } else {
                "&"
            }
        }
        SelfType.RefMut -> {
            if (type is TyReference) {
                ""
            } else {
                "&mut "
            }
        }
    }
}
