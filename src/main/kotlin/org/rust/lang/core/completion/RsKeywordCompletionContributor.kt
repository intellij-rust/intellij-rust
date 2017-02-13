package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.util.ProcessingContext
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.impl.RsFile

/**
 * Completes Rust keywords
 *
 * TODO: checkout  org.jetbrains.kotlin.idea.completion.KeywordCompletion, it has some super cool ideas
 */
class RsKeywordCompletionContributor : CompletionContributor(), DumbAware {

    init {
        extend(CompletionType.BASIC, declarationPattern(),
            RsKeywordCompletionProvider("const", "enum", "extern", "fn", "impl", "mod", "pub", "static", "struct", "trait", "type", "unsafe", "use"))
        extend(CompletionType.BASIC, pubDeclarationPattern(),
            RsKeywordCompletionProvider("const", "enum", "extern", "fn", "mod", "static", "struct", "trait", "type", "unsafe", "use"))
        extend(CompletionType.BASIC, externDeclarationPattern(),
            RsKeywordCompletionProvider("crate", "fn"))
        extend(CompletionType.BASIC, unsafeDeclarationPattern(),
            RsKeywordCompletionProvider("fn", "impl", "trait", "extern"))
        extend(CompletionType.BASIC, usePattern(),
            RsKeywordCompletionProvider("self", "super"))
        extend(CompletionType.BASIC, newCodeStatementPattern(),
            RsKeywordCompletionProvider("return", "let"))
        extend(CompletionType.BASIC, letPattern(),
            RsKeywordCompletionProvider("mut"))
        extend(CompletionType.BASIC, loopFlowCommandPatern(),
            RsKeywordCompletionProvider("break", "continue"))

        extend(CompletionType.BASIC, elsePattern(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
                val elseBuilder = LookupElementBuilder
                    .create("else")
                    .bold()
                    .withTailText(" {...}")
                    .withInsertHandler { context, lookupElement ->
                        context.document.insertString(context.selectionEndOffset, " {  }")
                        EditorModificationUtil.moveCaretRelatively(context.editor, 3)

                    }

                val elseIfBuilder = LookupElementBuilder
                    .create("else if")
                    .bold()
                    .withTailText(" {...}")
                    .withInsertHandler { context, lookupElement ->
                        context.document.insertString(context.selectionEndOffset, "  { }")
                        EditorModificationUtil.moveCaretRelatively(context.editor, 1)
                    }

                // `else` is more common than `else if`
                result.addElement(PrioritizedLookupElement.withPriority(elseBuilder, CompletionEngine.KEYWORD_PRIORITY * 1.0001))
                result.addElement(PrioritizedLookupElement.withPriority(elseIfBuilder, CompletionEngine.KEYWORD_PRIORITY))
            }
        })
    }

    private fun declarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern())

    private fun pubDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("pub"))

    private fun externDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("extern"))

    private fun usePattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("use"))

    private fun unsafeDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("unsafe"))

    private fun newCodeStatementPattern(): PsiElementPattern.Capture<PsiElement> =
        baseCodeStatementPattern().and(statementBeginningPattern())

    private fun letPattern(): PsiElementPattern.Capture<PsiElement> =
        baseCodeStatementPattern().and(statementBeginningPattern("let"))

    private fun loopFlowCommandPatern(): PsiElementPattern.Capture<PsiElement> =
        RsPsiPattern.inAnyLoop.and(newCodeStatementPattern())

    private fun baseDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        psiElement<PsiElement>().andOr(
            psiElement().withParent(RsPath::class.java),
            psiElement().withParent(or(psiElement<RsModItem>(), psiElement<RsFile>()))
        )

    private fun baseCodeStatementPattern(): PsiElementPattern.Capture<PsiElement> =
        psiElement<PsiElement>()
            .inside(psiElement<RsFunction>())
            .andNot(psiElement().withParent(RsModItem::class.java))

    private fun statementBeginningPattern(vararg startWords: String): PsiElementPattern.Capture<PsiElement> =
        psiElement<PsiElement>()
            .withElementType(TokenSet.create(IDENTIFIER))
            .and(RsPsiPattern.onStatementBeginning(*startWords))

    private fun elsePattern(): PsiElementPattern.Capture<PsiElement> {
        val braceAfterIf = psiElement(RBRACE).withSuperParent(2, psiElement(IF_EXPR))
        return psiElement<PsiElement>()
            .afterLeafSkipping(psiElement(PsiWhiteSpace::class.java), braceAfterIf)
    }
}
