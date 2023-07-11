/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.annotator.getFunctionCallContext
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.changeSignature.*
import org.rust.ide.refactoring.suggestedNames
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.isMethod
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.stdext.mapToSet
import org.rust.stdext.numberSuffix
import kotlin.math.max

/**
 * This fix can add, remove or change the type of parameters of a function.
 */
class ChangeFunctionSignatureFix private constructor(
    argumentList: RsValueArgumentList,
    function: RsFunction,
    private val signature: Signature,
    private val priority: PriorityAction.Priority? = null
) : RsQuickFixBase<RsValueArgumentList>(argumentList), PriorityAction {
    @IntentionName
    private val fixText: String = run {
        val callableType = if (function.isMethod) RsBundle.message("intention.name.method") else RsBundle.message("intention.name.function")
        val name = function.name
        val changes = signature.actions.withIndex().filter { (_, item) -> item !is SignatureAction.KeepParameter }
        val arguments = getEffectiveArguments(argumentList, function)

        if (changes.size == 1) {
            val (index, action) = changes[0]
            val indexOffset = index + 1
            val ordinal = RsBundle.message("intention.name.", indexOffset, numberSuffix(indexOffset))
            val parameterFormat = function.valueParameters.getOrNull(index)?.pat?.formatParameter(index)

            when (action) {
                is SignatureAction.ChangeParameterType -> {
                    val argument = arguments[action.argumentIndex]
                    RsBundle.message("intention.name.change.type.to", parameterFormat?:"", callableType, name?:"", renderType(argument.type))
                }
                is SignatureAction.InsertArgument -> {
                    val argument = arguments[action.argumentIndex]
                    RsBundle.message("intention.name.add.as.parameter.to", renderType(argument.type), ordinal, callableType, name?:"")
                }
                SignatureAction.RemoveParameter -> RsBundle.message("intention.name.remove.from", parameterFormat?:"", callableType, name?:"")
                is SignatureAction.KeepParameter -> error("unreachable")
            }
        } else {
            val actions = signature.actions.filter { it !is SignatureAction.RemoveParameter }
            val signatureText = actions.joinToString(", ") { action ->
                when (action) {
                    is SignatureAction.InsertArgument -> RsBundle.message("intention.name.b.b2", renderType(arguments[action.argumentIndex].type))
                    is SignatureAction.KeepParameter -> renderType(
                        function.valueParameters[action.parameterIndex].typeReference?.normType ?: TyUnknown
                    )
                    is SignatureAction.ChangeParameterType -> RsBundle.message("intention.name.b.b", renderType(arguments[action.argumentIndex].type))
                    SignatureAction.RemoveParameter -> error("unreachable")
                }
            }

            RsBundle.message("intention.name.html.change.signature.to.html", name?:"", signatureText)
        }
    }

    override fun getText(): String = fixText
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.change.function.signature")

    override fun startInWriteAction(): Boolean = false

    override fun getPriority(): PriorityAction.Priority = priority ?: PriorityAction.Priority.NORMAL

    override fun invoke(project: Project, editor: Editor?, element: RsValueArgumentList) {
        val context = element.getFunctionCallContext() ?: return
        val function = context.function ?: return

        val usedNames = mutableSetOf<String>()
        getExistingParameterNames(usedNames, function)

        val config = RsChangeFunctionSignatureConfig.create(function)
        val factory = RsPsiFactory(project)

        val arguments = getEffectiveArguments(element, function)
        var parameterIndex = 0
        for ((index, item) in signature.actions.withIndex()) {
            when (item) {
                is SignatureAction.InsertArgument -> {
                    val expr = arguments.getOrNull(item.argumentIndex) ?: continue
                    val typeText = renderType(expr.type)
                    val typeReference = factory.tryCreateType(typeText) ?: factory.createType("()")
                    val name = generateName(suggestName(expr), usedNames)

                    config.parameters.add(index, Parameter(factory, name, ParameterProperty.fromItem(typeReference)))
                    config.additionalTypesToImport.add(expr.type)
                    parameterIndex++
                }
                is SignatureAction.RemoveParameter -> config.parameters.removeAt(parameterIndex)
                is SignatureAction.ChangeParameterType -> {
                    val original = config.parameters[parameterIndex]
                    val expr = arguments.getOrNull(item.argumentIndex) ?: continue
                    val typeText = renderType(expr.type)
                    val typeReference = factory.tryCreateType(typeText) ?: factory.createType("()")
                    config.parameters[parameterIndex] = Parameter(
                        factory,
                        original.patText,
                        ParameterProperty.fromItem(typeReference),
                        original.index
                    )
                    config.additionalTypesToImport.add(expr.type)
                    parameterIndex++
                }
                else -> parameterIndex++
            }
        }

        runChangeSignatureRefactoring(config)
    }

    companion object {
        fun createIfCompatible(
            arguments: RsValueArgumentList,
            function: RsFunction
        ): List<ChangeFunctionSignatureFix> {
            if (!RsChangeSignatureHandler.isChangeSignatureAvailable(function)) return emptyList()
            val context = arguments.getFunctionCallContext() ?: return emptyList()

            val errorArguments = arguments.inference?.diagnostics
                .orEmpty()
                .filter { it is RsDiagnostic.TypeError && it.element in arguments.exprList }
                .mapToSet { it.element }

            // Map is used to make sure that there is only a single fix for a given signature
            val signatureToFix = mutableMapOf<Signature, ChangeFunctionSignatureFix>()

            val argumentCount = arguments.exprList.size
            if (context.expectedParameterCount < argumentCount) {
                val effectiveArgumentCount = getEffectiveArguments(arguments, function).size

                for ((direction, priority) in listOf(
                    ArgumentScanDirection.Forward to PriorityAction.Priority.HIGH,
                    ArgumentScanDirection.Backward to PriorityAction.Priority.NORMAL
                )) {
                    val signature = calculateSignatureWithInsertion(function, arguments, direction)

                    // Check if parameter count would match the current argument count
                    if (signature.getActualParameterCount() != effectiveArgumentCount) {
                        continue
                    }

                    signatureToFix[signature] = ChangeFunctionSignatureFix(arguments, function, signature, priority)
                }
            }

            if (signatureToFix.isEmpty()) {
                val simpleSignature = calculateSignatureWithoutInsertion(function, arguments, errorArguments)
                if (!simpleSignature.actions.all { it is SignatureAction.KeepParameter }) {
                    signatureToFix[simpleSignature] = ChangeFunctionSignatureFix(arguments, function, simpleSignature)
                }
            }

            return signatureToFix.values.toList()
        }
    }

    sealed class ArgumentScanDirection {
        open fun <T> map(iterable: Iterable<T>): Iterable<T> = iterable

        object Forward : ArgumentScanDirection()
        object Backward : ArgumentScanDirection() {
            override fun <T> map(iterable: Iterable<T>): Iterable<T> = iterable.reversed()
        }
    }
}

private fun RsPat.formatParameter(index: Int): String {
    return if (this is RsPatIdent) {
        "parameter `${patBinding.name}`"
    } else {
        val num = index + 1
        "`$num${numberSuffix(num)}` parameter"
    }
}

private fun suggestName(expr: RsExpr): String {
    if (expr is RsPathExpr) {
        val reference = expr.path.reference?.resolve()
        val name = (reference as? RsPatBinding)?.name

        if (name != null) {
            return name
        }
    }

    return expr.suggestedNames().default
}

private fun generateName(defaultName: String, usedNames: MutableSet<String>): String {
    var name = defaultName
    var index = 0
    while (name in usedNames) {
        name = "${defaultName}$index"
        index += 1
    }
    usedNames.add(name)
    return name
}

/**
 * Calculates a possible new signature in the specified direction.
 * This method tries to insert new parameters into the signature (possibly in the middle).
 * Assumes that there are more arguments than parameters.
 */
private fun calculateSignatureWithInsertion(
    function: RsFunction,
    argumentList: RsValueArgumentList,
    direction: ChangeFunctionSignatureFix.ArgumentScanDirection
): Signature {
    val parameters = function.valueParameters
    val arguments = getEffectiveArguments(argumentList, function)

    if (parameters.isEmpty()) {
        return Signature(arguments.indices.map { SignatureAction.InsertArgument(it) })
    }

    val ctx = function.implLookup.ctx

    fun <T> createIterator(items: List<T>): PeekableIterator<T> = PeekableIterator(direction.map(items).iterator())

    val parameterIterator = createIterator(parameters)
    val argumentIterator = createIterator(arguments)

    val insertions = mutableListOf<SignatureAction>()
    while (true) {
        val parameter = parameterIterator.value
        val argument = argumentIterator.value ?: break

        if (parameter != null && ctx.combineTypes(parameter.typeReference?.normType(ctx) ?: TyUnknown, argument.type).isOk) {
            insertions.add(SignatureAction.KeepParameter(parameters.indexOf(parameter)))
            parameterIterator.advance()
            argumentIterator.advance()
        } else {
            insertions.add(SignatureAction.InsertArgument(arguments.indexOf(argument)))
            argumentIterator.advance()
        }
    }

    while (true) {
        val parameter = parameterIterator.value ?: break
        insertions.add(SignatureAction.KeepParameter(parameters.indexOf(parameter)))
        parameterIterator.advance()
    }

    return Signature(direction.map(insertions).toList())
}

/**
 * Calculates a possible new signature without shuffling new parameters into the signature.
 * New parameters can only be added to the end.
 * Goes linearly through parameter/argument pairs and calculates actions that should be performed to parameters to match
 * the arguments.
 */
private fun calculateSignatureWithoutInsertion(
    function: RsFunction,
    args: RsValueArgumentList,
    errorArguments: Set<PsiElement>
): Signature {
    val actions = mutableListOf<SignatureAction>()

    val parameters = function.valueParameters
    val arguments = getEffectiveArguments(args, function)

    val length = max(arguments.size, parameters.size)
    for (index in 0 until length) {
        val action = when {
            index >= arguments.size -> SignatureAction.RemoveParameter
            index >= parameters.size -> SignatureAction.InsertArgument(index)
            arguments[index] in errorArguments -> SignatureAction.ChangeParameterType(index)
            else -> SignatureAction.KeepParameter(index)
        }
        actions.add(action)
    }

    return Signature(actions)
}

/**
 * Returns argument expressions.
 * If the call is UFCS, removes self argument from the argument list.
 */
private fun getEffectiveArguments(args: RsValueArgumentList, function: RsFunction): List<RsExpr> {
    val arguments = args.exprList

    val isUFCS = function.isMethod && args.parent is RsCallExpr
    return if (isUFCS) {
        arguments.drop(1)
    } else {
        arguments
    }
}

private class PeekableIterator<T>(private val iterator: Iterator<T>) {
    var value: T? = null

    init {
        advance()
    }

    fun advance() {
        value = if (iterator.hasNext()) {
            iterator.next()
        } else {
            null
        }
    }
}

private data class Signature(val actions: List<SignatureAction>) {
    /**
     * Calculates how many parameters would a function had if these actions were applied to its signature.
     */
    fun getActualParameterCount(): Int = actions.sumOf {
        val count = when (it) {
            is SignatureAction.RemoveParameter -> -1
            else -> 1
        }
        count
    }
}

private sealed class SignatureAction {
    data class KeepParameter(val parameterIndex: Int) : SignatureAction()
    data class InsertArgument(val argumentIndex: Int) : SignatureAction()

    // Parameters may only be removed at the end
    object RemoveParameter : SignatureAction()
    data class ChangeParameterType(val argumentIndex: Int) : SignatureAction()
}

private fun renderType(ty: Ty): String = ty.renderInsertionSafe()

private fun getExistingParameterNames(usedNames: MutableSet<String>, function: RsFunction) {
    val visitor = object : RsRecursiveVisitor() {
        override fun visitPatBinding(o: RsPatBinding) {
            val name = o.identifier.text
            usedNames.add(name)
        }
    }
    function.valueParameterList?.acceptChildren(visitor)
}
