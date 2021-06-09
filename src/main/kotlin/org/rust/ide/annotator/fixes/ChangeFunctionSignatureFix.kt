/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.annotator.getFunctionCallContext
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.changeSignature.*
import org.rust.ide.refactoring.suggestedNames
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.isMethod
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

/**
 * Requires that the number of arguments is larger than the number of parameters.
 */
class ChangeFunctionSignatureFix private constructor(
    arguments: RsValueArgumentList,
    function: RsFunction,
    private val direction: ArgumentScanDirection,
) : LocalQuickFixAndIntentionActionOnPsiElement(arguments), PriorityAction {
    private val fixText: String = run {
        val signature = calculateSignature(function, arguments, direction)
        val argumentInsertions = signature.withIndex().filter { (_, item) -> item is SignatureMember.Argument }

        val callableType = if (function.isMethod) "method" else "function"
        val name = function.name
        if (argumentInsertions.size == 1) {
            val index = argumentInsertions[0].index + 1
            val argument = argumentInsertions[0].value as SignatureMember.Argument

            val ordinal = "${index}${suffix(index)}"
            "Add `${renderType(argument.expr.type)}` as `$ordinal` parameter to $callableType `$name`"
        } else {
            val signatureText = signature.joinToString(", ") {
                when (it) {
                    is SignatureMember.Argument -> "<b>${renderType(it.expr.type)}</b>"
                    is SignatureMember.Parameter -> it.parameter.typeReference?.text.orEmpty()
                }
            }

            "<html>Change signature of $name($signatureText)</html>"
        }
    }

    override fun getText(): String = fixText
    override fun getFamilyName(): String = "Add parameter to function"

    override fun startInWriteAction(): Boolean = false

    override fun getPriority(): PriorityAction.Priority = when (direction) {
        ArgumentScanDirection.Forward -> PriorityAction.Priority.HIGH
        ArgumentScanDirection.Backward -> PriorityAction.Priority.NORMAL
    }

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val args = startElement as? RsValueArgumentList ?: return
        val context = when (val parent = args.parent) {
            is RsCallExpr -> parent.getFunctionCallContext()
            is RsMethodCall -> parent.getFunctionCallContext()
            else -> null
        } ?: return
        val function = context.function ?: return

        val usedNames = mutableSetOf<String>()
        getExistingParameterNames(usedNames, function)

        val config = RsChangeFunctionSignatureConfig.create(function)
        val signature = calculateSignature(function, args, direction)
        val factory = RsPsiFactory(project)

        for ((index, item) in signature.withIndex()) {
            if (item is SignatureMember.Argument) {
                val expr = item.expr
                val typeText = renderType(expr.type)
                val fragment = RsTypeReferenceCodeFragment(project, typeText, context = args)
                val typeReference = fragment.typeReference ?: factory.createType("()")
                val name = generateName(suggestName(expr), usedNames)

                config.parameters.add(index, Parameter(factory, name, ParameterProperty.fromItem(typeReference)))
                config.additionalTypesToImport.add(expr.type)
            }
        }

        runChangeSignatureRefactoring(config)
    }

    companion object {
        fun createIfCompatible(
            arguments: RsValueArgumentList,
            function: RsFunction,
            direction: ArgumentScanDirection
        ): ChangeFunctionSignatureFix? {
            if (function.containingCrate?.origin != PackageOrigin.WORKSPACE) return null

            val signature = calculateSignature(function, arguments, direction)
            val parameterInsertions = signature.filterIsInstance<SignatureMember.Parameter>()

            // There are parameters that were not matched by any argument
            if (function.valueParameters.size < parameterInsertions.size) {
                return null
            }

            return ChangeFunctionSignatureFix(arguments, function, direction)
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
 * Assumes that there are more arguments than parameters.
 */
private fun calculateSignature(
    function: RsFunction,
    argumentList: RsValueArgumentList,
    direction: ChangeFunctionSignatureFix.ArgumentScanDirection
): List<SignatureMember> {
    val parameters = function.valueParameters
    val arguments = argumentList.exprList

    if (parameters.isEmpty()) {
        return arguments.map { SignatureMember.Argument(it) }
    }

    val ctx = function.implLookup.ctx

    fun <T> createIterator(items: List<T>): PeekableIterator<T> = PeekableIterator(direction.map(items).iterator())

    val parameterIterator = createIterator(parameters)
    val argumentIterator = createIterator(arguments)

    val insertions = mutableListOf<SignatureMember>()
    while (true) {
        val parameter = parameterIterator.value
        val argument = argumentIterator.value ?: break

        if (parameter != null && ctx.combineTypes(parameter.typeReference?.type ?: TyUnknown, argument.type).isOk) {
            insertions.add(SignatureMember.Parameter(parameter))
            parameterIterator.advance()
            argumentIterator.advance()
        } else {
            insertions.add(SignatureMember.Argument(argument))
            argumentIterator.advance()
        }
    }

    return direction.map(insertions).toList()
}

private class PeekableIterator<T>(private val iterator: Iterator<T>) {
    var value: T? = null

    init {
        if (iterator.hasNext()) {
            value = iterator.next()
        }
    }

    fun advance() {
        value = if (iterator.hasNext()) {
            iterator.next()
        } else {
            null
        }
    }
}

private sealed class SignatureMember {
    data class Parameter(val parameter: RsValueParameter) : SignatureMember()
    data class Argument(val expr: RsExpr) : SignatureMember()
}

private fun suffix(number: Int): String {
    if ((number % 100) in 11..13) {
        return "th"
    }
    return when (number % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

private fun renderType(ty: Ty): String =
    ty.renderInsertionSafe(
        skipUnchangedDefaultTypeArguments = true,
        useAliasNames = true,
        includeLifetimeArguments = true
    )

private fun getExistingParameterNames(usedNames: MutableSet<String>, function: RsFunction) {
    val visitor = object : RsRecursiveVisitor() {
        override fun visitPatBinding(o: RsPatBinding) {
            val name = o.identifier.text
            usedNames.add(name)
        }
    }
    function.valueParameterList?.acceptChildren(visitor)
}
