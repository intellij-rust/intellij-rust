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
import com.intellij.psi.util.parentsOfType
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.GenericConstraints
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

class CreateFunctionIntention : RsElementBaseIntentionAction<CreateFunctionIntention.Context>() {
    override fun getFamilyName() = "Create function"

    sealed class FunctionInsertionTarget {
        abstract val module: RsMod

        class Module(val target: RsMod): FunctionInsertionTarget() {
            override val module: RsMod = target
        }
        class Item(val item: RsStructOrEnumItemElement): FunctionInsertionTarget() {
            override val module: RsMod = item.containingMod
        }
    }

    sealed class Context(val name: String, val callElement: PsiElement) {
        abstract val visibility: String
        open val isAsync: Boolean = callElement.isEdition2018
        abstract val arguments: RsValueArgumentList
        abstract val returnType: Ty?
        open val implItem: RsImplItem? = null

        open class Function(callExpr: RsCallExpr, name: String, val module: RsMod) : Context(name, callExpr) {
            override val visibility: String = when {
                callExpr.containingCrate != module.containingCrate -> "pub "
                callExpr.containingMod != module -> "pub(crate) "
                else -> ""
            }
            override val isAsync: Boolean = super.isAsync
                && (callExpr.parent as? RsDotExpr)?.fieldLookup?.isAsync == true
            override val arguments: RsValueArgumentList = callExpr.valueArgumentList
            override val returnType: Ty? = callExpr.expectedType
        }

        class AssociatedFunction(
            callExpr: RsCallExpr,
            name: String,
            module: RsMod,
            val item: RsStructOrEnumItemElement
        ) : Function(callExpr, name, module) {
            override val implItem: RsImplItem?
                get() = super.implItem
        }

        class Method(val methodCall: RsMethodCall, name: String, val item: RsStructOrEnumItemElement)
            : Context(name, methodCall) {
            override val visibility: String
                get() {
                    val parentImpl = methodCall.parentOfType<RsImplItem>()
                    return when {
                        // creating a method inside the same impl
                        (parentImpl?.typeReference?.type as? TyAdt)?.item == item && parentImpl.traitRef == null -> ""
                        methodCall.containingCrate != item.containingCrate -> "pub "
                        else -> "pub(crate)"
                    }
                }
            override val isAsync: Boolean = super.isAsync
                && (methodCall.parentDotExpr.parent as? RsDotExpr)?.fieldLookup?.isAsync == true
            override val arguments: RsValueArgumentList = methodCall.valueArgumentList
            override val returnType: Ty? = methodCall.parentDotExpr.expectedType
        }
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.parentOfType<RsPath>()
        val functionCall = path?.parentOfType<RsCallExpr>()
        if (functionCall != null) {
            if (!functionCall.expr.isAncestorOf(path)) return null
            if (path.resolveStatus != PathResolveStatus.UNRESOLVED) return null

            val target = getTargetItemForFunction(path) ?: return null
            val name = path.referenceName ?: return null

            return if (target is FunctionInsertionTarget.Item) {
                text = "Create associated function `${target.item.name}::$name`"
                Context.AssociatedFunction(functionCall, name, target.module, target.item)
            } else {
                text = "Create function `$name`"
                Context.Function(functionCall, name, target.module)
            }
        }
        val methodCall = element.parentOfType<RsMethodCall>()
        if (methodCall != null) {
            if (methodCall.reference.resolve() != null) return null
            if (element != methodCall.identifier) return null

            val name = methodCall.identifier.text
            val type = methodCall.parentDotExpr.expr.type.stripReferences() as? TyAdt ?: return null
            if (type.item.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return null

            text = "Create method `$name`"
            return Context.Method(methodCall, name, type.item)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val function = buildCallable(project, ctx) ?: return
        val inserted = insertCallable(ctx, function) ?: return

        if (inserted.containingFile == ctx.callElement.containingFile) {
            val toBeReplaced = inserted.rawValueParameters.flatMap { listOfNotNull(it.pat, it.typeReference) } +
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
        val async = if (ctx.isAsync) "async" else ""
        if (ctx is Context.Method) {
            parameters.add(0, "&self")
        }
        val returnType = if (config.returnType !is TyUnit) {
            " -> ${config.returnType.renderInsertionSafe(useAliasNames = true)}"
        } else ""
        val paramsText = parameters.joinToString(", ")

        return factory.tryCreateFunction("$visibility $async fn $functionName$genericParams($paramsText)$returnType $whereClause {\n    unimplemented!()\n}")
    }

    private fun getTargetItemForFunction(path: RsPath): FunctionInsertionTarget? {
        if (path.qualifier != null) {
            val item = path.qualifier?.reference?.resolve() as? RsQualifiedNamedElement
            if (item?.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return null
            if (!isUnitTestMode && !item.isWritable) return null

            return when (item) {
                is RsMod -> FunctionInsertionTarget.Module(item)
                is RsStructOrEnumItemElement -> FunctionInsertionTarget.Item(item)
                else -> null
            }
        }
        return FunctionInsertionTarget.Module(path.containingMod)
    }

    private data class CallableConfig(
        val parameters: List<String>,
        val returnType: Ty,
        val genericConstraints: GenericConstraints
    )

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
            val params = callExpr.parentOfType<RsImplItem>()?.typeParameters.orEmpty()
            genericConstraints.withoutTypes(params)
        } else genericConstraints

        return CallableConfig(parameters, returnType, filteredConstraints)
    }

    private fun insertCallable(ctx: Context, function: RsFunction): RsFunction? {
        val sourceFunction = ctx.callElement.parentOfType<RsFunction>() ?: return null

        return when (ctx) {
            is Context.AssociatedFunction -> insertAssociatedFunction(ctx.item, function)
            is Context.Function -> insertFunction(ctx.module, sourceFunction, function)
            is Context.Method -> insertMethod(ctx.item, sourceFunction, function)
        }
    }

    private fun insertAssociatedFunction(
        item: RsStructOrEnumItemElement,
        function: RsFunction
    ): RsFunction? {
        val psiFactory = RsPsiFactory(item.project)
        val name = item.name ?: return null

        val newImpl = psiFactory.createInherentImplItem(name, item.typeParameterList, item.whereClause)
        val impl = item.parent.addAfter(newImpl, item) as RsImplItem

        return impl.members?.let {
            it.addBefore(function, it.rbrace) as RsFunction
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
