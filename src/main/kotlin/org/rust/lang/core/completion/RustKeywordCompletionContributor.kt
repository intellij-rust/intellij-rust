package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.RustPsiPattern
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.RustModItemElement
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.impl.RustFile

/**
 * Completes Rust keywords
 */
class RustKeywordCompletionContributor : CompletionContributor(), DumbAware {

    init {
        extend(CompletionType.BASIC, moduleTopLevelPattern(),
            RustKeywordCompletionProvider("enum", "extern crate", "struct", "trait", "type", "use"))
        extend(CompletionType.BASIC, returnPattern(), RustKeywordCompletionProvider("return", "let"))
    }

    private fun moduleTopLevelPattern(): PsiElementPattern.Capture<PsiElement> {
        return statementBeginningPattern(RustTokenElementTypes.IDENTIFIER)
            .withSuperParent(1, or(psiElement<RustModItemElement>(), psiElement<RustFile>()))
    }

    private fun returnPattern(): PsiElementPattern.Capture<PsiElement> {
        return statementBeginningPattern(RustTokenElementTypes.IDENTIFIER)
            .inside(or(psiElement<RustFunctionElement>(), psiElement<RustImplMethodMemberElement>()))
    }

    private fun statementBeginningPattern(vararg tokenTypes: IElementType): PsiElementPattern.Capture<PsiElement> {
        return psiElement<PsiElement>()
            .withElementType(TokenSet.create(*tokenTypes)).with(RustPsiPattern.OnStatementBeginning())
    }
}
