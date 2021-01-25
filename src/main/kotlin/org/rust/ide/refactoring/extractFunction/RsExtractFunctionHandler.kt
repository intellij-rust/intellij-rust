/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractFunction

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.usageView.UsageInfo
import org.rust.ide.refactoring.RsRenameProcessor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

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
        WriteCommandAction.writeCommandAction(file).run<Throwable> {
            val psiFactory = RsPsiFactory(project)
            val extractedFunction = addExtractedFunction(project, config, psiFactory) ?: return@run
            replaceOldStatementsWithCallExpr(config, psiFactory)
            renameFunctionParameters(extractedFunction, config.valueParameters.filter { it.isSelected }.map { it.name })
        }
    }

    private fun addExtractedFunction(project: Project, config: RsExtractFunctionConfig, psiFactory: RsPsiFactory): RsFunction? {
        val owner = config.function.owner

        val function = psiFactory.createFunction(config.functionText)
        val psiParserFacade = PsiParserFacade.SERVICE.getInstance(project)
        return when {
            owner is RsAbstractableOwner.Impl && !owner.isInherent -> {
                val beforeNewline = psiParserFacade.createWhiteSpaceFromText("\n")
                val afterNewline = psiParserFacade.createWhiteSpaceFromText("\n")
                val type = owner.impl.typeReference!!
                val parent = owner.impl.parent
                //FIXME: Don't create new impl if a other impl exists
                val impl = psiFactory.createImpl(type.text, listOf(function))
                val newImpl = parent.addAfter(impl, parent.addAfter(beforeNewline, owner.impl)) as? RsImplItem
                parent.addAfter(afterNewline, newImpl)
                newImpl?.members?.childOfType<RsFunction>()
            }
            else -> {
                val newline = psiParserFacade.createWhiteSpaceFromText("\n\n")
                val end = config.function.block?.rbrace ?: return null
                config.function.addAfter(function, config.function.addAfter(newline, end)) as? RsFunction
            }
        }
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

    private fun replaceOldStatementsWithCallExpr(config: RsExtractFunctionConfig, psiFactory: RsPsiFactory) {
        var stmt = ""
        if (config.returnValue?.exprText != null) {
            stmt += "let ${config.returnValue.exprText} = "
        }
        val firstParameter = config.parameters.firstOrNull()
        stmt += if (firstParameter != null && firstParameter.isSelf) {
            "self.${config.name}(${config.argumentsText})"
        } else {
            val type = when (val owner = config.function.owner) {
                is RsAbstractableOwner.Impl -> {
                    owner.impl.typeReference?.text?.let {
                        if (owner.impl.typeParameterList == null) it else "<$it>"
                    }
                }
                is RsAbstractableOwner.Trait -> "Self"
                else -> null
            }
            "${if (type != null) "$type::" else ""}${config.name}(${config.argumentsText})"
        }
        if (config.isAsync) {
            stmt += ".await"
        }
        val element = if (config.returnValue == null || config.returnValue.exprText != null) {
            stmt += ";"
            psiFactory.createStatement(stmt)
        } else {
            psiFactory.createExpression(stmt)
        }
        config.elements.forEachIndexed { index, psiElement ->
            if (index == 0) {
                psiElement.replace(element)
            } else {
                psiElement.delete()
            }
        }
    }
}
