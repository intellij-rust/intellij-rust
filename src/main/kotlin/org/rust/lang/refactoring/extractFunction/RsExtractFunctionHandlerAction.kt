/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsFunctionOwner
import org.rust.lang.core.psi.ext.owner

class RsExtractFunctionHandlerAction(
    project: Project?,
    file: PsiFile,
    val config: RsExtractFunctionConfig
) : WriteCommandAction<Any>(project, file) {

    override fun run(result: Result<Any>) {
        val psiFactory = RsPsiFactory(project)
        if (addExtractedFunction(psiFactory)) {
            replaceOldStatementsWithCallExpr(psiFactory)
        }
    }

    private fun addExtractedFunction(psiFactory: RsPsiFactory): Boolean {
        val owner = config.containingFunction.owner

        val function = psiFactory.createFunction(
            config.name,
            config.elements,
            config.visibilityLevelPublic,
            config.needsSelf
        )
        when {
            owner is RsFunctionOwner.Impl && !owner.isInherent -> {
                val beforeNewline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")
                val afterNewline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")
                val type = owner.impl.typeReference!!
                val parent = owner.impl.parent
                //FIXME: Don't create new impl if a other impl exists
                val impl = psiFactory.createImpl(type.text, listOf(function))
                parent.addAfter(afterNewline,
                    parent.addAfter(impl,
                        parent.addAfter(beforeNewline, owner.impl)))
            }
            else -> {
                val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n\n")
                val end = config.containingFunction.block?.rbrace ?: return false
                config.containingFunction.addAfter(function, config.containingFunction.addAfter(newline, end))
            }
        }
        return true
    }

    private fun replaceOldStatementsWithCallExpr(psiFactory: RsPsiFactory) {
        val call = if (config.needsSelf) {
            psiFactory.createFunctionCallSelfMethodStmt(config.name)
        } else {
            psiFactory.createFunctionCallFunctionStmt(config.name, (config.containingFunction.owner as? RsFunctionOwner.Impl)?.impl?.typeReference?.text)
        }
        config.elements.forEachIndexed { index, psiElement ->
            if (index == 0) {
                psiElement.replace(call)
            } else {
                psiElement.delete()
            }
        }
    }
}
