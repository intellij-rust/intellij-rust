/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.changeSignature.ParameterInfo
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.usageView.UsageInfo
import org.rust.ide.intentions.visibility.ChangeVisibilityIntention
import org.rust.ide.refactoring.findBinding
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyUnit

sealed class RsFunctionUsage(val element: RsElement) : UsageInfo(element) {
    open val isCallUsage: Boolean = false

    class FunctionCall(val call: RsCallExpr) : RsFunctionUsage(call) {
        override val isCallUsage: Boolean = true
    }

    class MethodCall(val call: RsMethodCall) : RsFunctionUsage(call) {
        override val isCallUsage: Boolean = true
    }

    class Reference(val path: RsPath) : RsFunctionUsage(path)
    class MethodImplementation(val overriddenMethod: RsFunction) : RsFunctionUsage(overriddenMethod)
}

fun findFunctionUsages(function: RsFunction): Sequence<RsFunctionUsage> = function.findUsages().map {
    when (it) {
        is RsCallExpr -> RsFunctionUsage.FunctionCall(it)
        is RsMethodCall -> RsFunctionUsage.MethodCall(it)
        is RsPath -> RsFunctionUsage.Reference(it)
        else -> error("unreachable")
    }
}

fun processFunctionUsage(config: RsChangeFunctionSignatureConfig, usage: RsFunctionUsage) {
    val function = config.function
    val factory = RsPsiFactory(function.project)
    if (config.nameChanged()) {
        renameFunctionUsage(factory, usage, config)
    }
    if (usage.isCallUsage && config.parameterSetOrOrderChanged()) {
        changeArguments(factory, usage, config, function.isMethod)
    }
}

fun processFunction(
    project: Project,
    config: RsChangeFunctionSignatureConfig,
    function: RsFunction,
    changeSignature: Boolean
) {
    val factory = RsPsiFactory(project)

    val parameters = function.valueParameterList?.valueParameterList
    if (parameters != null) {
        renameParameterUsages(parameters, config.parameters)
    }

    if (!changeSignature) return

    if (config.nameChanged()) {
        rename(factory, function, config)
    }
    changeVisibility(function, config)
    changeReturnType(factory, function, config)

    changeParameters(factory, function, config)
    changeAsync(factory, function, config)
    changeUnsafe(factory, function, config)

    for (type in config.additionalTypesToImport) {
        RsImportHelper.importTypeReferencesFromTy(function, type, useAliases = true)
    }
}

private fun rename(factory: RsPsiFactory, function: RsFunction, config: RsChangeFunctionSignatureConfig) {
    function.identifier.replace(factory.createIdentifier(config.name))
}

private fun renameFunctionUsage(
    factory: RsPsiFactory,
    usage: RsFunctionUsage,
    config: RsChangeFunctionSignatureConfig
) {
    val identifier = factory.createIdentifier(config.name)
    when (usage) {
        is RsFunctionUsage.Reference -> usage.path.referenceNameElement?.replace(identifier)
        is RsFunctionUsage.FunctionCall -> {
            val path = (usage.call.expr as? RsPathExpr)?.path ?: return
            path.referenceNameElement?.replace(identifier)
        }
        is RsFunctionUsage.MethodCall -> usage.call.identifier.replace(identifier)
    }
}

fun changeVisibility(function: RsFunction, config: RsChangeFunctionSignatureConfig) {
    if (function.vis?.text == config.visibility?.text) return

    function.vis?.delete()

    val vis = config.visibility
    if (vis != null) {
        val anchor = ChangeVisibilityIntention.findInsertionAnchor(function)
        function.addBefore(vis, anchor)
    }
}

private fun changeReturnType(factory: RsPsiFactory, function: RsFunction, config: RsChangeFunctionSignatureConfig) {
    if (!areTypesEqual(function.retType?.typeReference, config.returnTypeReference)) {
        function.retType?.delete()
        if (config.returnType !is TyUnit) {
            val ret = factory.createRetType(config.returnTypeReference.text)
            function.addAfter(ret, function.valueParameterList) as RsRetType
            RsImportHelper.importTypeReferencesFromTy(function, config.returnType,
                useAliases = true, skipUnchangedDefaultTypeArguments = true)
        }
    }
}

private fun changeArguments(
    factory: RsPsiFactory,
    usage: RsFunctionUsage,
    config: RsChangeFunctionSignatureConfig,
    isMethod: Boolean
) {
    val arguments = when (usage) {
        is RsFunctionUsage.FunctionCall -> usage.call.valueArgumentList
        is RsFunctionUsage.MethodCall -> usage.call.valueArgumentList
        else -> error("unreachable")
    }
    for (parameter in config.parameters) {
        val defaultValue = parameter.defaultValue.item ?: continue
        RsImportHelper.importTypeReferencesFromElements(arguments, setOf(defaultValue),
            useAliases = true, skipUnchangedDefaultTypeArguments = true)
    }
    val argumentsCopy = arguments.copy() as RsValueArgumentList
    val argumentsList = argumentsCopy.exprList
    val isUFCS = isMethod && usage is RsFunctionUsage.FunctionCall
    fixParametersOrder(
        factory,
        arguments,
        argumentsCopy,
        if (isUFCS) argumentsList.first() else null,
        if (isUFCS) argumentsList.drop(1) else argumentsList,
        config
    ) { it.defaultValue.item }
}

private fun changeParameters(factory: RsPsiFactory, function: RsFunction, config: RsChangeFunctionSignatureConfig) {
    val parameters = function.valueParameterList ?: return

    importParameterTypes(config.parameters, function)
    changeParametersNameAndType(parameters.valueParameterList, config.parameters)
    if (!config.parameterSetOrOrderChanged()) return

    val parametersCopy = parameters.copy() as RsValueParameterList
    fixParametersOrder(
        factory,
        parameters,
        parametersCopy,
        parametersCopy.selfParameter,
        parametersCopy.valueParameterList,
        config
    ) { factory.createValueParameter(it.patText, it.typeReference, reference = false) }
}

private fun changeParametersNameAndType(parameters: List<RsValueParameter>, descriptors: List<Parameter>) {
    for (descriptor in descriptors) {
        if (descriptor.index == ParameterInfo.NEW_PARAMETER) continue
        val psi = parameters[descriptor.index]
        changeParameterNameAndType(psi, descriptor)
    }
}

private fun changeParameterNameAndType(psi: RsValueParameter, descriptor: Parameter) {
    if (descriptor.patText != psi.pat?.text) {
        psi.pat?.replace(descriptor.pat)
    }
    if (!areTypesEqual(descriptor.typeReference, psi.typeReference)) {
        psi.typeReference?.replace(descriptor.typeReference)
    }
}

private fun renameParameterUsages(parameters: List<RsValueParameter>, descriptors: List<Parameter>) {
    for (descriptor in descriptors) {
        if (descriptor.index != ParameterInfo.NEW_PARAMETER) {
            val psiParameter = parameters[descriptor.index]
            val binding = psiParameter.pat?.findBinding()
            if (binding != null) {
                if (descriptor.patText != psiParameter.pat?.text) {
                    if (descriptor.pat is RsPatIdent && psiParameter.pat is RsPatIdent) {
                        val newName = descriptor.patText
                        val usages = RenameUtil.findUsages(binding, newName, false, false, emptyMap())
                        for (info in usages) {
                            RenameUtil.rename(info, newName)
                        }
                    }
                }
            }
        }
    }
}

private fun fixParametersOrder(
    factory: RsPsiFactory,
    /** [RsValueArgumentList] or [RsValueParameterList] */
    parameters: RsElement,
    parametersCopy: RsElement,
    parameterSelf: RsElement?,
    parametersList: List<RsElement>,
    config: RsChangeFunctionSignatureConfig,
    createNewParameter: (Parameter) -> PsiElement?
) {
    if (parametersList.size != config.originalParameters.size) return
    val descriptors = config.parameters

    // remove old parameters
    val lparen = parameters.firstChild
    val rparen = parameters.lastChild
    if (lparen.nextSibling != rparen) {
        parameters.deleteChildRange(lparen.nextSibling, rparen.prevSibling)
    }
    if (descriptors.isEmpty() && parameterSelf == null) return

    // collect parameters psi in right order
    val isMultiline = parametersCopy.textContains('\n')
    val selfGroup = parameterSelf?.collectSurroundingWhiteSpaceAndComments()
    val groupsWithoutSelf = descriptors.map { descriptor ->
        if (descriptor.index == ParameterInfo.NEW_PARAMETER) {
            val newline = if (isMultiline) factory.createNewline() else null
            val psi = createNewParameter(descriptor)
            listOfNotNull(newline, psi)
        } else {
            val psi = parametersList[descriptor.index]
            psi.collectSurroundingWhiteSpaceAndComments()
        }
    }
    val groups = listOfNotNull(selfGroup) + groupsWithoutSelf

    // add parameters one by one
    val rspace = parametersCopy.lastChild.prevSibling as? PsiWhiteSpace
    val isSingleParameter = groups.size == 1 && descriptors.size < config.originalParameters.size
    val hasTrailingComma = parametersList.lastOrNull()?.getNextNonCommentSibling()?.elementType == COMMA
    for (group in groups) {
        if (group !== groups.first()) {
            parameters.addBefore(factory.createComma(), rparen)
        }
        for (element in group) {
            if (element === rspace) continue  // will be added after loop
            parameters.addBefore(element, rparen)
        }
    }

    if (isSingleParameter) {
        val lspace = parameters.firstChild.nextSibling
        if (lspace is PsiWhiteSpace) lspace.delete()
    } else {
        if (hasTrailingComma && isMultiline) {
            parameters.addBefore(factory.createComma(), rparen)
        }

        if (rspace != null) parameters.addBefore(rspace, rparen)
    }
}

private fun PsiElement.collectSurroundingWhiteSpaceAndComments(): List<PsiElement> {
    val first = getPrevNonCommentSibling()?.nextSibling ?: this
    val last = getNextNonCommentSibling() ?: nextSibling
    val elements = generateSequence(first) { it.nextSibling?.takeIf { next -> next !== last } }
    return elements.toList()
}

private fun importParameterTypes(descriptors: List<Parameter>, context: RsElement) {
    for (descriptor in descriptors) {
        RsImportHelper.importTypeReferencesFromElements(context, setOf(descriptor.typeReference),
            useAliases = true, skipUnchangedDefaultTypeArguments = true)
    }
}

private fun changeAsync(factory: RsPsiFactory, function: RsFunction, config: RsChangeFunctionSignatureConfig) {
    val async = function.node.findChildByType(RsElementTypes.ASYNC)?.psi
    if (config.isAsync) {
        if (async == null) {
            val asyncKw = factory.createAsyncKeyword()
            function.addBefore(asyncKw, function.unsafe ?: function.fn)
        }
    } else {
        async?.delete()
    }
}

private fun changeUnsafe(factory: RsPsiFactory, function: RsFunction, config: RsChangeFunctionSignatureConfig) {
    if (config.isUnsafe) {
        if (function.unsafe == null) {
            val unsafe = factory.createUnsafeKeyword()
            function.addBefore(unsafe, function.fn)
        }
    } else {
        function.unsafe?.delete()
    }
}

private fun areTypesEqual(t1: RsTypeReference?, t2: RsTypeReference?): Boolean = (t1?.text ?: "()") == (t2?.text
    ?: "()")
