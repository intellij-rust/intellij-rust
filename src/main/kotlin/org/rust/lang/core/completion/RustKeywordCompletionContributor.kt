package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.RustPsiPattern
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustModItemElement
import org.rust.lang.core.psi.RustPathElement
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.impl.RustFile

/**
 * Completes Rust keywords
 */
class RustKeywordCompletionContributor : CompletionContributor(), DumbAware {

    init {
        extend(CompletionType.BASIC, declarationPattern(),
            RustKeywordCompletionProvider("enum", "extern", "fn", "impl", "mod", "pub", "struct", "trait", "type", "unsafe", "use"))
        extend(CompletionType.BASIC, pubDeclarationPattern(),
            RustKeywordCompletionProvider("enum", "extern", "fn", "mod", "struct", "trait", "type", "unsafe", "use"))
        extend(CompletionType.BASIC, externDeclarationPattern(),
            RustKeywordCompletionProvider("crate", "fn"))
        extend(CompletionType.BASIC, unsafeDeclarationPattern(),
            RustKeywordCompletionProvider("fn", "impl", "trait", "extern"))
        extend(CompletionType.BASIC, newCodeStatementPattern(),
            RustKeywordCompletionProvider("return", "let"))
        extend(CompletionType.BASIC, letPattern(),
            RustKeywordCompletionProvider("mut"))
    }

    private fun declarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern())

    private fun pubDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("pub"))

    private fun externDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("extern"))

    private fun unsafeDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("unsafe"))

    private fun newCodeStatementPattern(): PsiElementPattern.Capture<PsiElement> =
        baseCodeStatementPattern().and(statementBeginningPattern())

    private fun letPattern(): PsiElementPattern.Capture<PsiElement> =
        baseCodeStatementPattern().and(statementBeginningPattern("let"))

    private fun baseDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        psiElement<PsiElement>().andOr(
            psiElement().withParent(RustPathElement::class.java),
            psiElement().withParent(or(psiElement<RustModItemElement>(), psiElement<RustFile>()))
        )

    private fun baseCodeStatementPattern(): PsiElementPattern.Capture<PsiElement> =
        psiElement<PsiElement>()
            .inside(psiElement<RustFunctionElement>())
            .andNot(psiElement().withParent(RustModItemElement::class.java))

    private fun statementBeginningPattern(vararg startWords: String): PsiElementPattern.Capture<PsiElement> =
        psiElement<PsiElement>()
            .withElementType(TokenSet.create(RustTokenElementTypes.IDENTIFIER))
            .and(RustPsiPattern.onStatementBeginning(*startWords))
}
