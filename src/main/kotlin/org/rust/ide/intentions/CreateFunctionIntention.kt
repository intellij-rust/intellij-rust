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
import org.rust.lang.core.resolve.TraitImplSource
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class CreateFunctionIntention : RsElementBaseIntentionAction<CreateFunctionIntention.Context>() {
    override fun getFamilyName() = "Create function"

    sealed class Context(val name: String, val callElement: PsiElement) {
        abstract val visibility: String
        abstract val arguments: RsValueArgumentList
        abstract val returnType: Ty?

        class Function(val callExpr: RsCallExpr, name: String, val module: RsMod) : Context(name, callExpr) {
            override val visibility: String = if (callExpr.containingMod != module) "pub(crate) " else ""
            override val arguments: RsValueArgumentList = callExpr.valueArgumentList
            override val returnType: Ty? = callExpr.expectedType
        }

        class Method(val methodCall: RsMethodCall, name: String, val item: RsStructOrEnumItemElement)
            : Context(name, methodCall) {
            override val visibility: String
                get() {
                    val parentImpl = methodCall.parentOfType<RsImplItem>()
                    // creating a method inside the same impl
                    return if ((parentImpl?.typeReference?.type as? TyAdt)?.item == item && parentImpl.traitRef == null) {
                        ""
                    } else "pub(crate)"
                }
            override val arguments: RsValueArgumentList = methodCall.valueArgumentList
            override val returnType: Ty? = methodCall.parentDotExpr.expectedType
        }
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>()
        val functionCall = path?.parentOfType<RsCallExpr>()
        if (functionCall != null) {
            if (!path.isUnresolved) return null
            if (!functionCall.expr.isAncestorOf(path)) return null

            val module = getTargetModuleForFunction(path) ?: return null
            val name = path.referenceName
            text = "Create function `$name`"
            return Context.Function(functionCall, name, module)
        }
        val methodCall = element.parentOfType<RsMethodCall>()
        if (methodCall != null) {
            if (methodCall.reference.resolve() != null) return null
            if (element != methodCall.identifier && element != methodCall.valueArgumentList.lparen) return null

            val name = methodCall.identifier.text
            val type = methodCall.parentDotExpr.expr.type.stripReferences() as? TyAdt ?: return null

            text = "Create method `$name`"
            return Context.Method(methodCall, name, type.item)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val function = buildCallable(project, ctx) ?: return
        val inserted = insertCallable(ctx, function) ?: return

        if (inserted.containingFile == ctx.callElement.containingFile) {
            val toBeReplaced = inserted.valueParameters.flatMap { listOfNotNull(it.pat, it.typeReference) } +
                listOfNotNull(inserted.block?.expr)
            editor.buildAndRunTemplate(inserted, toBeReplaced.map { it.createSmartPointer() })
        } else {
            // template builder cannot be used for a different file
            inserted.navigate(true)
        }
    }

    private fun buildCallable(project: Project, ctx: Context): RsFunction? {
        val functionName = ctx.name

        val factory = RsPsiFactory(project)
        val config = getCallableConfig(ctx)

        val genericParams = config.genericConstraints.buildTypeParameters()
        val parameters = config.parameters.toMutableList()
        val whereClause = config.genericConstraints.buildWhereClause()
        val visibility = ctx.visibility
        if (ctx is Context.Method) {
            parameters.add(0, "&self")
        }
        val returnType = if (config.returnType !is TyUnit) {
            " -> ${config.returnType.renderInsertionSafe(useAliasNames = true)}"
        } else ""
        val paramsText = parameters.joinToString(", ")

        return factory.tryCreateFunction("$visibility fn $functionName$genericParams($paramsText)$returnType $whereClause {\n    unimplemented!()\n}")
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

    private data class CallableConfig(val parameters: List<String>,
                                      val returnType: Ty,
                                      val genericConstraints: GenericConstraints)

    private fun getCallableConfig(ctx: Context): CallableConfig {
        val callExpr = ctx.callElement
        val arguments = ctx.arguments

        val parameters = arguments.exprList.mapIndexed { index, expr ->
            "p$index: ${expr.type.renderInsertionSafe(useAliasNames = true)}"
        }

        val returnType = ctx.returnType.takeIf { it != TyUnknown } ?: TyUnit
        val genericConstraints = GenericConstraints.create(callExpr)
            .filterByTypes(arguments.exprList.map { it.type }.plus(returnType))

        val filteredConstraints = if (ctx is Context.Method) {
            val params = ctx.callElement.parentOfType<RsImplItem>()?.typeParameters.orEmpty()
            genericConstraints.withoutTypes(params)
        } else genericConstraints

        return CallableConfig(parameters, returnType, filteredConstraints)
    }

    private fun insertCallable(ctx: Context, function: RsFunction): RsFunction? {
        val sourceFunction = ctx.callElement.parentOfType<RsFunction>() ?: return null

        return when (ctx) {
            is Context.Function -> insertFunction(ctx.module, sourceFunction, function)
            is Context.Method -> insertMethod(ctx.item, sourceFunction, function)
        }
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

            val target: RsItemElement = impl ?: sourceFunction
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

    private fun insertMethod(
        item: RsStructOrEnumItemElement,
        sourceFunction: RsFunction,
        function: RsFunction
    ): RsFunction? {
        val impl = getOrCreateImpl(item, sourceFunction)
        return impl.members?.let {
            it.addBefore(function, it.rbrace) as RsFunction
        }
    }

    private fun getOrCreateImpl(item: RsStructOrEnumItemElement, sourceFunction: RsFunction): RsImplItem {
        val owner = sourceFunction.owner
        if (owner is RsAbstractableOwner.Impl) {
            val impl = owner.impl
            if (impl.traitRef == null && (impl.typeReference?.type as? TyAdt)?.item == item) {
                return impl
            }
        }

        val newImpl = RsPsiFactory(item.project).createInherentImplItem(item.name
            ?: "?", item.typeParameterList, item.whereClause)
        return item.parent.addAfter(newImpl, item) as RsImplItem
    }
}
