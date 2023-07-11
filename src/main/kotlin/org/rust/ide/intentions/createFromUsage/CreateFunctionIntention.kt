/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.createFromUsage

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.intentions.RsElementBaseIntentionAction
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.GenericConstraints
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.ide.utils.import.RsImportHelper
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.ide.utils.template.canRunTemplateFor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

class CreateFunctionIntention : RsElementBaseIntentionAction<CreateFunctionIntention.Context>() {
    override fun getFamilyName() = RsBundle.message("intention.family.name.create.function")

    sealed interface Context {
        val name: String
        val callElement: RsElement
        val place: FunctionInsertionPlace
        val arguments: RsValueArgumentList
        val returnType: ReturnType
        val visibility: String
        val isAsync: Boolean get() = callElement.isAtLeastEdition2018

        open class Function(
            override val callElement: RsCallExpr,
            override val name: String,
            override val place: FunctionInsertionPlace,
            private val module: RsMod,
        ) : Context {
            override val arguments: RsValueArgumentList get() = callElement.valueArgumentList
            override val returnType: ReturnType get() = ReturnType.create(callElement)
            override val visibility: String get() = getVisibility(module, callElement.containingMod)
            override val isAsync: Boolean get() = super.isAsync
                && (callElement.parent as? RsDotExpr)?.fieldLookup?.isAsync == true
        }

        class Method(
            override val callElement: RsMethodCall,
            override val name: String,
            override val place: FunctionInsertionPlace,
            private val item: RsStructOrEnumItemElement,
        ) : Context {
            override val arguments: RsValueArgumentList get() = callElement.valueArgumentList
            override val returnType: ReturnType get() = ReturnType.create(callElement.parentDotExpr)
            override val visibility: String
                get() {
                    val parentImpl = callElement.contextStrict<RsImplItem>()
                    return when {
                        // creating a method inside the same impl
                        (parentImpl?.typeReference?.rawType as? TyAdt)?.item == item && parentImpl.traitRef == null -> ""
                        callElement.containingCrate != item.containingCrate -> "pub "
                        else -> "pub(crate)"
                    }
                }
            override val isAsync: Boolean = super.isAsync
                && (callElement.parentDotExpr.parent as? RsDotExpr)?.fieldLookup?.isAsync == true
        }
    }

    class ReturnType(val type: Ty, val needsTemplate: Boolean) {
        companion object {
            fun create(expr: RsExpr): ReturnType {
                val expected = expr.expectedType ?: TyUnknown

                // Show template if the type is unknown and there is something that expects the return value
                val parent = expr.parent
                val needsTemplate = expected is TyUnknown && when (parent) {
                    is RsExprStmt -> false
                    is RsDotExpr -> parent.fieldLookup?.identifier?.text != "await"
                    else -> true
                }
                return if (needsTemplate) {
                    ReturnType(expected, true)
                } else {
                    ReturnType(expected.takeIf { it !is TyUnknown } ?: TyUnit.INSTANCE, false)
                }
            }
        }
    }

    sealed interface FunctionInsertionPlace {
        class In(val place: PsiInsertionPlace): FunctionInsertionPlace
        class InNewImplIn(
            val placeForImpl: PsiInsertionPlace,
            val itemName: String,
            val item: RsStructOrEnumItemElement
        ): FunctionInsertionPlace
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val path = element.contextStrict<RsPath>()
        val functionCall = path?.contextStrict<RsCallExpr>()
        if (functionCall != null) {
            if (!functionCall.expr.isContextOf(path)) return null
            if (path.resolveStatus != PathResolveStatus.UNRESOLVED) return null

            val target = getTargetItemForFunctionCall(path) ?: return null
            val name = path.referenceName ?: return null

            return when (target) {
                is RsMod -> {
                    val place = FunctionInsertionPlace.In(
                        PsiInsertionPlace.forItemInModAfter(target, functionCall) ?: return null
                    )
                    text = RsBundle.message("intention.name.create.function", name)
                    Context.Function(functionCall, name, place, target)
                }

                is RsStructOrEnumItemElement -> {
                    val place = FunctionInsertionPlace.InNewImplIn(
                        PsiInsertionPlace.forItemAfter(target) ?: return null,
                        target.name ?: return null,
                        target
                    )
                    text = RsBundle.message("intention.name.create.associated.function", target.name ?: "", name)
                    Context.Function(functionCall, name, place, target.containingMod)
                }

                is RsImplItem -> {
                    val place = FunctionInsertionPlace.In(PsiInsertionPlace.forTraitOrImplMember(target) ?: return null)
                    text = RsBundle.message("intention.name.create.associated.function.self", name)
                    Context.Function(functionCall, name, place, target.containingMod)
                }

                else -> null
            }
        }
        val methodCall = element.contextStrict<RsMethodCall>()
        if (methodCall != null) {
            if (methodCall.reference.multiResolve().isNotEmpty()) return null
            if (element != methodCall.identifier) return null

            val name = methodCall.identifier.text
            val type = methodCall.parentDotExpr.expr.type.stripReferences() as? TyAdt ?: return null
            if (type.item.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return null

            val ancestorImpl = methodCall.contextStrict<RsImplItem>()
            val place = if (ancestorImpl != null && ancestorImpl.traitRef == null && (ancestorImpl.typeReference?.normType as? TyAdt)?.item == type.item) {
                FunctionInsertionPlace.In(PsiInsertionPlace.forTraitOrImplMember(ancestorImpl) ?: return null)
            } else {
                FunctionInsertionPlace.InNewImplIn(
                    PsiInsertionPlace.forItemAfter(type.item) ?: return null,
                    type.item.name ?: return null,
                    type.item
                )
            }

            text = RsBundle.message("intention.name.create.method", name)
            return Context.Method(methodCall, name, place, type.item)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val function = buildCallable(project, ctx)
        val inserted = insertCallable(ctx, function)

        val types = ctx.arguments.exprList.map { it.type } + ctx.returnType.type
        RsImportHelper.importTypeReferencesFromTys(inserted, types)

        if (editor.canRunTemplateFor(inserted)) {
            val toBeReplaced = inserted.rawValueParameters
                .flatMap { listOfNotNull(it.pat, it.typeReference) }
                .toMutableList()

            if (ctx.returnType.needsTemplate) {
                toBeReplaced += listOfNotNull(inserted.retType?.typeReference)
            }

            toBeReplaced += listOfNotNull(inserted.block?.syntaxTailStmt)
            editor.buildAndRunTemplate(inserted, toBeReplaced)
        } else {
            // template builder cannot be used for a different file
            inserted.navigate(true)
        }
    }

    private fun buildCallable(project: Project, ctx: Context): RsFunction {
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
            " -> ${config.returnType.renderInsertionSafe()}"
        } else ""
        val paramsText = parameters.joinToString(", ")

        return factory.createFunction("$visibility $async fn $functionName$genericParams($paramsText)$returnType $whereClause {\n    todo!()\n}")
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
            "p$index: ${expr.type.renderInsertionSafe()}"
        }

        val returnType = ctx.returnType.type.takeIf { it != TyUnknown } ?: TyUnit.INSTANCE
        val genericConstraints = GenericConstraints.create(callExpr)
            .filterByTypes(arguments.exprList.map { it.type }.plus(returnType))

        val filteredConstraints = if (ctx is Context.Method) {
            val params = callExpr.contextStrict<RsImplItem>()?.typeParameters.orEmpty()
            genericConstraints.withoutTypes(params)
        } else genericConstraints

        return CallableConfig(parameters, ctx.returnType.type, filteredConstraints)
    }

    private fun insertCallable(ctx: Context, function: RsFunction): RsFunction {
        return when (val place = ctx.place) {
            is FunctionInsertionPlace.In -> {
                place.place.insert(function)
            }
            is FunctionInsertionPlace.InNewImplIn -> {
                val psiFactory = RsPsiFactory(function.project)
                val newImpl = psiFactory.createInherentImplItem(place.itemName, place.item.typeParameterList, place.item.whereClause)
                val insertedImpl = place.placeForImpl.insert(newImpl)
                insertedImpl.members!!.let {
                    it.addBefore(function, it.rbrace) as RsFunction
                }
            }
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY
}
