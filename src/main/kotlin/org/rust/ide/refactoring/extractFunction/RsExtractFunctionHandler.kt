/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractFunction

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import org.rust.ide.presentation.PsiRenderingOptions
import org.rust.ide.presentation.RsPsiRenderer
import org.rust.ide.presentation.renderTypeReference
import org.rust.ide.refactoring.RsRenameProcessor
import org.rust.ide.surroundWith.addStatement
import org.rust.ide.utils.GenericConstraints
import org.rust.ide.utils.import.RsImportHelper.importTypeReferencesFromTys
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.RsCachedImplItem
import org.rust.lang.core.types.ty.TyUnit
import org.rust.openapiext.runWriteCommandAction

class RsExtractFunctionHandler : RefactoringActionHandler {
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called from the editor.
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        if (file !is RsFile) return
        val start = editor?.selectionModel?.selectionStart
        val end = editor?.selectionModel?.selectionEnd
        if (start === null || end === null) return
        val config = RsExtractFunctionConfig.create(file, start, end) ?: return

        extractFunctionDialog(project, config) {
            extractFunction(project, file, config)
        }
    }

    private fun extractFunction(project: Project, file: PsiFile, config: RsExtractFunctionConfig) {
        project.runWriteCommandAction(
            RefactoringBundle.message("extract.method.title"),
            file
        ) {
            val psiFactory = RsPsiFactory(project)
            val extractedFunction = addExtractedFunction(project, config, psiFactory) ?: return@runWriteCommandAction
            replaceOldStatementsWithCallExpr(config, psiFactory)
            val parameters = config.valueParameters.filter { it.isSelected }
            renameFunctionParameters(extractedFunction, parameters.map { it.name })
            importTypeReferencesFromTys(extractedFunction, config.parametersAndReturnTypes)
        }
    }

    private fun addExtractedFunction(
        project: Project,
        config: RsExtractFunctionConfig,
        psiFactory: RsPsiFactory
    ): RsFunction? {
        val owner = config.function.owner

        val function = psiFactory.createFunction(config.functionText)
        adjustControlFlow(config, function)
        val psiParserFacade = PsiParserFacade.getInstance(project)
        return when {
            owner is RsAbstractableOwner.Impl && !owner.isInherent -> {
                val impl = findExistingInherentImpl(owner.impl) ?: createNewInherentImpl(owner.impl) ?: return null
                val members = impl.members ?: return null
                members.addBefore(psiParserFacade.createWhiteSpaceFromText("\n\n"), members.rbrace)
                members.addBefore(function, members.rbrace) as? RsFunction
            }
            else -> {
                val newline = psiParserFacade.createWhiteSpaceFromText("\n\n")
                val end = config.function.block?.rbrace ?: return null
                config.function.addAfter(function, config.function.addAfter(newline, end)) as? RsFunction
            }
        }
    }

    private fun adjustControlFlow(config: RsExtractFunctionConfig, function: RsFunction) {
        val factory = RsPsiFactory(function.project)
        val block = function.block!!
        val syntaxTailStmt = block.syntaxTailStmt
        val (_, controlFlowElements) = findControlFlowElements(listOf(block)) ?: return

        when (config.returnKind) {
            ReturnKind.VALUE -> Unit
            ReturnKind.BOOL -> {
                controlFlowElements.replaceEachWithReturn(factory) { "true" }
                syntaxTailStmt?.addSemicolonIfNeeded()
                block.addStatement(factory.createStatement("false"))
            }
            ReturnKind.OPTION_CONTROL_FLOW -> {
                controlFlowElements.replaceEachWithReturn(factory) { "Some($it)" }
                syntaxTailStmt?.addSemicolonIfNeeded()
                block.addStatement(factory.createStatement("None"))
            }
            ReturnKind.OPTION_VALUE -> {
                controlFlowElements.replaceEachWithReturn(factory) { "None" }
                syntaxTailStmt?.wrapWith("Some")
            }
            ReturnKind.RESULT -> {
                controlFlowElements.replaceEachWithReturn(factory) { "Err($it)" }
                syntaxTailStmt?.wrapWith("Ok")
            }
            ReturnKind.TRY_OPERATOR -> {
                val constructor = config.controlFlow!!.tryOperatorInfo!!.successVariant
                val syntaxTailExpr = syntaxTailStmt?.expr
                when {
                    syntaxTailExpr is RsTryExpr && syntaxTailStmt.semicolon == null -> {
                        syntaxTailExpr.replace(syntaxTailExpr.expr)
                    }
                    config.outputVariables.type is TyUnit -> {
                        syntaxTailStmt?.addSemicolonIfNeeded()
                        block.addStatement(factory.createStatement("$constructor(())"))
                    }
                    else -> syntaxTailStmt?.wrapWith(constructor)
                }
            }
        }
    }

    private fun List<PsiElement>.replaceEachWithReturn(factory: RsPsiFactory, getValue: (String?) -> String) {
        for (element in this) {
            if (element is RsTryExpr) continue
            val oldValue = element.getControlFlowValue()?.text
            val newValue = getValue(oldValue)
            element.replace(factory.createExpression("return $newValue"))
        }
    }

    private fun PsiElement.getControlFlowValue(): PsiElement? =
        when (this) {
            is RsRetExpr -> expr
            is RsBreakExpr -> expr
            else -> null
        }

    private fun RsExprStmt.wrapWith(constructor: String) {
        val exprNew = RsPsiFactory(project).createExpression("$constructor($text)")
        expr.replace(exprNew)
    }

    /**
     * Finds inherent impl corresponding to [traitImpl].
     * Impls at same tree level are checked (e.g. if [traitImpl] is top-level impl, then top-level impls are checked).
     */
    private fun findExistingInherentImpl(traitImpl: RsImplItem): RsImplItem? {
        check(traitImpl.traitRef != null)
        val cachedTraitImpl = RsCachedImplItem.forImpl(traitImpl)
        return (traitImpl.parent as? RsItemsOwner)
            ?.childrenOfType<RsImplItem>()
            ?.firstOrNull { impl ->
                val cachedImpl = RsCachedImplItem.forImpl(impl)
                val (_, generics, constGenerics) = cachedImpl.typeAndGenerics ?: return@firstOrNull false
                cachedImpl.isInherent && cachedImpl.isValid && !cachedImpl.isNegativeImpl
                    && generics.isEmpty() && constGenerics.isEmpty()  // TODO: Support generics
                    && cachedImpl.typeAndGenerics == cachedTraitImpl.typeAndGenerics
            }
    }

    private fun createNewInherentImpl(traitImpl: RsImplItem): RsImplItem? {
        val parent = traitImpl.parent
        val psiFactory = RsPsiFactory(parent.project)

        val typeReference = traitImpl.typeReference!!
        val constraints = GenericConstraints.create(traitImpl).filterByTypeReferences(listOf(typeReference))

        val renderer = RsPsiRenderer(PsiRenderingOptions())

        val typeParameters = constraints.buildTypeParameters()
        val typeText = renderer.renderTypeReference(typeReference)
        val whereClause = constraints.buildWhereClause()

        val text = "impl$typeParameters $typeText $whereClause{}"
        val newImpl = psiFactory.tryCreateImplItem(text) ?: return null

        val newImplCopy = parent.addAfter(newImpl, traitImpl) as RsImplItem
        parent.addBefore(psiFactory.createWhitespace("\n\n"), newImplCopy)
        return newImplCopy
    }

    /**
     * Original function signature and body are inserted at first.
     * Then it is necessary to change the names of original parameters to the real (renamed) parameters' names.
     */
    private fun renameFunctionParameters(function: RsFunction, newNames: List<String>) {
        val parameters = function.rawValueParameters
            .map { it.pat }
            .filterIsInstance(RsPatIdent::class.java)
            .map { it.patBinding }

        for ((parameter, newName) in parameters.zip(newNames)) {
            if (newName != parameter.name) {
                val parameterUsages = ReferencesSearch.search(parameter, LocalSearchScope(function)).findAll()
                val usageInfo = parameterUsages.map { UsageInfo(it) }.toTypedArray()
                RsRenameProcessor().renameElement(parameter, newName, usageInfo, null)
            }
        }
    }

    private fun replaceOldStatementsWithCallExpr(config: RsExtractFunctionConfig, factory: RsPsiFactory) {
        val call = generateFunctionCallFull(config)

        for (element in config.elements.dropLast(1)) {
            element.delete()
        }
        when (val last = config.elements.last()) {
            is RsExpr -> last.replace(factory.createExpression(call.removeSuffix(";")))
            is RsStmt -> last.replace(factory.createStatement(call))
        }
    }

    private fun generateFunctionCallFull(config: RsExtractFunctionConfig): String {
        val call = generateFunctionCall(config)
        val letPrefix = config.outputVariables.exprText?.let { "let $it = " } ?: ""
        val needSemicolon = letPrefix != ""
            || config.outputVariables.type is TyUnit && (config.controlFlow == null || config.returnKind == ReturnKind.TRY_OPERATOR)
        val controlFlow = config.controlFlow?.text
        return when (config.returnKind) {
            ReturnKind.VALUE -> "$letPrefix$call"
            ReturnKind.BOOL -> "if $call { $controlFlow; }"
            ReturnKind.OPTION_CONTROL_FLOW -> "if let Some(value) = $call {\n$controlFlow value;\n}"
            ReturnKind.OPTION_VALUE -> "${letPrefix}match $call {\nSome(value) => value,\nNone => $controlFlow,\n}"
            ReturnKind.RESULT -> "${letPrefix}match $call {\nOk(value) => value,\nErr(value) => $controlFlow value,\n}"
            ReturnKind.TRY_OPERATOR -> "${letPrefix}$call?"
        } + if (needSemicolon) ";" else ""
    }

    private fun generateFunctionCall(config: RsExtractFunctionConfig): String {
        val self = when {
            config.parameters.firstOrNull()?.isSelf == true -> "self."
            config.function.owner.isImplOrTrait -> "Self::"
            else -> ""
        }
        val await = if (config.isAsync) ".await" else ""
        return "$self${config.name}(${config.argumentsText})$await"
    }
}
