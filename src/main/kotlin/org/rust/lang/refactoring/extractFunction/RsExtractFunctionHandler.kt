/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.refactoring.RefactoringActionHandler
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner

class RsExtractFunctionHandler : RefactoringActionHandler {
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        //this doesn't get called form the editor.
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
            if (addExtractedFunction(project, config, psiFactory)) {
                replaceOldStatementsWithCallExpr(config, psiFactory)
            }
        }
    }

    private fun addExtractedFunction(project: Project, config: RsExtractFunctionConfig, psiFactory: RsPsiFactory): Boolean {
        val owner = config.containingFunction.owner

        val function = psiFactory.createFunction(config)
        val psiParserFacade = PsiParserFacade.SERVICE.getInstance(project)
        when {
            owner is RsAbstractableOwner.Impl && !owner.isInherent -> {
                val beforeNewline = psiParserFacade.createWhiteSpaceFromText("\n")
                val afterNewline = psiParserFacade.createWhiteSpaceFromText("\n")
                val type = owner.impl.typeReference!!
                val parent = owner.impl.parent
                //FIXME: Don't create new impl if a other impl exists
                val impl = psiFactory.createImpl(type.text, listOf(function))
                parent.addAfter(afterNewline,
                    parent.addAfter(impl,
                        parent.addAfter(beforeNewline, owner.impl)))
            }
            else -> {
                val newline = psiParserFacade.createWhiteSpaceFromText("\n\n")
                val end = config.containingFunction.block?.rbrace ?: return false
                config.containingFunction.addAfter(function, config.containingFunction.addAfter(newline, end))
            }
        }
        return true
    }

    private fun replaceOldStatementsWithCallExpr(config: RsExtractFunctionConfig, psiFactory: RsPsiFactory) {
        var stmt = ""
        if (config.returnValue?.expression != null) {
            stmt += "let ${config.returnValue.expression} = "
        }
        val firstParameter = config.parameters.firstOrNull()
        stmt += if (firstParameter != null && firstParameter.name.endsWith("self") && firstParameter.type == null) {
            "self.${config.name}(${config.argumentsText})"
        } else {
            val owner = config.containingFunction.owner
            val type = when (owner) {
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
        val element = if (config.returnValue == null || config.returnValue.expression != null ) {
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
