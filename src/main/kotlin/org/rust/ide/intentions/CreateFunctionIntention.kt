/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.GenericConstraints
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class CreateFunctionIntention : RsElementBaseIntentionAction<CreateFunctionIntention.Context>() {
    override fun getFamilyName() = text

    sealed class Context {
        data class Function(val callExpr: RsCallExpr, val name: String, val module: RsMod) : Context()
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>()
        val functionCall = path?.parentOfType<RsCallExpr>() ?: return null
        if (!path.isUnresolved) return null

        if (!functionCall.expr.isAncestorOf(path)) return null

        val module = getTargetModuleForFunction(path) ?: return null
        val name = path.referenceName
        text = "Create function `$name`"
        return Context.Function(functionCall, name, module)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        when (ctx) {
            is Context.Function -> createFunction(project, editor, ctx)
        }
    }
}

private fun createFunction(project: Project, editor: Editor, ctx: CreateFunctionIntention.Context.Function) {
    val functionName = ctx.name
    val callExpr = ctx.callExpr

    val factory = RsPsiFactory(project)
    val config = getFunctionConfig(callExpr)

    val genericParams = config.genericConstraints.buildTypeParameters()
    val params = config.parameters.joinToString(", ")
    val whereClause = config.genericConstraints.buildWhereClause()

    val vis = if (callExpr.containingMod != ctx.module) "pub(crate) " else ""
    val returnType = if (config.returnType !is TyUnit) {
        " -> ${config.returnType.renderInsertionSafe(useAliasNames = true)}"
    } else ""
    val function = factory.tryCreateFunction("${vis}fn $functionName$genericParams($params)$returnType $whereClause {\n    unimplemented!()\n}")
        ?: return

    val sourceFunction = callExpr.parentOfType<RsFunction>() ?: return
    val inserted = insertFunction(ctx.module, sourceFunction, function)

    if (ctx.module.containingFile == callExpr.containingFile) {
        val toBeReplaced = inserted.valueParameters.flatMap { listOfNotNull(it.pat, it.typeReference) } +
            listOfNotNull(inserted.block?.expr)
        editor.buildAndRunTemplate(inserted, toBeReplaced.map { it.createSmartPointer() })
    } else {
        // template builder cannot be used for a different file
        inserted.navigate(true)
    }
}

private fun getTargetModuleForFunction(path: RsPath): RsMod? {
    if (path.qualifier != null) {
        val mod = path.qualifier?.reference?.resolve() as? RsMod
        if (mod?.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return null
        if (!isUnitTestMode && !mod.isWritable) return null
        return mod
    }
    return path.containingMod
}

private data class FunctionConfig(val parameters: List<String>,
                                  val returnType: Ty,
                                  val genericConstraints: GenericConstraints)

private fun getFunctionConfig(callExpr: RsCallExpr): FunctionConfig {
    val parameters = callExpr.valueArgumentList.exprList.mapIndexed { index, expr ->
        "p$index: ${expr.type.renderInsertionSafe(useAliasNames = true)}"
    }

    val returnType = callExpr.expectedType.takeIf { it != TyUnknown } ?: TyUnit

    val genericConstraints = GenericConstraints.create(callExpr)
        .filterByTypes(callExpr.valueArgumentList.exprList.map { it.type }.plus(returnType))

    return FunctionConfig(parameters, returnType, genericConstraints)
}

private fun insertFunction(
    targetModule: RsMod,
    sourceFunction: RsFunction,
    function: RsFunction
): RsFunction {
    if (targetModule == sourceFunction.containingMod) {
        val impl: RsTraitOrImpl? = when (val owner = sourceFunction.owner) {
            is RsAbstractableOwner.Trait -> owner.trait
            is RsAbstractableOwner.Impl -> owner.impl
            else -> null
        }

        val target = impl ?: sourceFunction
        return target.parent.addAfter(function, target) as RsFunction
    }

    // add to the end of module/file
    return if (targetModule is RsModItem) {
        targetModule.addBefore(function, targetModule.rbrace)
    } else {
        if (targetModule.lastChild == null) {
            targetModule.add(function)
        } else {
            targetModule.addAfter(function, targetModule.lastChild)
        }
    } as RsFunction
}
