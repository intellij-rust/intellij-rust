package org.rust.lang.refactoring.extractFunction

import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.lang.core.psi.*

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
        val function = psiFactory.createFunction(
            config.name,
            config.elements,
            config.visibilityLevelPublic,
            config.isMethod()
        )
        val anchor = config.anchor
        when (config.implType) {
            RsWrapperType.TraitMethod,
            RsWrapperType.TraitFunction -> {
                val beforeNewline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")
                val afterNewline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")
                val implTrait = anchor?.parent as? RsImplItem ?: return false
                val type = implTrait.typeReference ?: return false
                val parent = implTrait.parent
                //FIXME: Don't create new impl if a other impl exists
                val impl = psiFactory.createImpl(type.text, listOf(function))
                parent.addAfter(afterNewline,
                    parent.addAfter(impl,
                        parent.addAfter(beforeNewline, implTrait)))
            }
            RsWrapperType.Function,
            RsWrapperType.ImplMethod,
            RsWrapperType.ImplFunction -> {
                val newline = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n\n")
                val end = anchor?.block?.rbrace ?: return false
                anchor.addAfter(function, anchor.addAfter(newline, end))
            }
        }
        return true
    }

    private fun replaceOldStatementsWithCallExpr(psiFactory: RsPsiFactory) {
        val call = if (config.isMethod()) {
            psiFactory.createFunctionCallSelfMethodStmt(config.name)
        } else {
            psiFactory.createFunctionCallFunctionStmt(config.name, (config.anchor?.parent as? RsImplItem)?.typeReference?.text)
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
