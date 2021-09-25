/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.ProcessingContext
import org.rust.lang.core.*
import org.rust.lang.core.RsPsiPattern.baseDeclarationPattern
import org.rust.lang.core.RsPsiPattern.baseInherentImplDeclarationPattern
import org.rust.lang.core.RsPsiPattern.baseTraitOrImplDeclaration
import org.rust.lang.core.RsPsiPattern.declarationPattern
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*

/**
 * Completes Rust keywords
 *
 * TODO: checkout  org.jetbrains.kotlin.idea.completion.KeywordCompletion, it has some super cool ideas
 */
class RsKeywordCompletionContributor : CompletionContributor(), DumbAware {

    init {
        extend(CompletionType.BASIC, declarationPattern(),
            RsKeywordCompletionProvider("const", "enum", "extern", "fn", "impl", "mod", "static", "struct", "trait", "type", "union", "unsafe", "use"))
        extend(CompletionType.BASIC, afterVisDeclarationPattern(),
            RsKeywordCompletionProvider("const", "enum", "extern", "fn", "mod", "static", "struct", "trait", "type", "union", "unsafe", "use"))
        extend(CompletionType.BASIC, externDeclarationPattern(),
            RsKeywordCompletionProvider("crate", "fn"))
        extend(CompletionType.BASIC, unsafeDeclarationPattern(),
            RsKeywordCompletionProvider("fn", "impl", "trait", "extern"))
        extend(CompletionType.BASIC, newCodeStatementPattern(),
            RsKeywordCompletionProvider("return", "let"))
        extend(CompletionType.BASIC, letPattern(),
            RsKeywordCompletionProvider("mut"))
        extend(CompletionType.BASIC, loopFlowCommandPattern(),
            RsKeywordCompletionProvider("break", "continue"))
        extend(CompletionType.BASIC, wherePattern(),
            RsKeywordCompletionProvider("where"))
        extend(CompletionType.BASIC, constParameterBeginningPattern(),
            RsKeywordCompletionProvider("const"))
        extend(CompletionType.BASIC, traitOrImplDeclarationPattern(),
            RsKeywordCompletionProvider("const", "fn", "type", "unsafe"))
        extend(CompletionType.BASIC, unsafeTraitOrImplDeclarationPattern(),
            RsKeywordCompletionProvider("fn"))
        extend(CompletionType.BASIC, afterVisInherentImplDeclarationPattern(),
            RsKeywordCompletionProvider("const", "fn", "type", "unsafe"))

        extend(CompletionType.BASIC, ifElsePattern(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val elseBuilder = elseLookupElement()
                val elseIfBuilder = conditionLookupElement("else if")
                // `else` is more common than `else if`
                result.addElement(elseBuilder.withPriority(KEYWORD_PRIORITY * 1.0001))
                result.addElement(elseIfBuilder.withPriority(KEYWORD_PRIORITY))
            }
        })

        extend(CompletionType.BASIC, letElsePattern(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val elseBuilder = elseLookupElement()
                result.addElement(elseBuilder.withPriority(KEYWORD_PRIORITY))
            }
        })

        extend(CompletionType.BASIC, pathExpressionPattern(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                for (keyword in CONDITION_KEYWORDS) {
                    result.addElement(conditionLookupElement(keyword).withPriority(KEYWORD_PRIORITY))
                }
            }
        })
    }

    private fun conditionLookupElement(lookupString: String): LookupElementBuilder {
        return LookupElementBuilder
            .create(lookupString)
            .bold()
            .withTailText(" {...}")
            .withInsertHandler { context, _ ->
                val isLetExpr = context.file.findElementAt(context.tailOffset - 1)
                    ?.ancestorStrict<RsLetDecl>()
                    ?.let { it.expr?.text == lookupString } == true
                val hasSemicolon = context.nextCharIs(';')

                var tail = "  { }"
                if (isLetExpr && !hasSemicolon) tail += ';'
                context.document.insertString(context.selectionEndOffset, tail)
                EditorModificationUtil.moveCaretRelatively(context.editor, 1)
            }
    }

    private fun afterVisDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(afterVis())

    private fun externDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("extern"))

    private fun unsafeDeclarationPattern(): PsiElementPattern.Capture<PsiElement> =
        baseDeclarationPattern().and(statementBeginningPattern("unsafe"))

    private fun newCodeStatementPattern(): PsiElementPattern.Capture<PsiElement> =
        baseCodeStatementPattern().and(statementBeginningPattern())

    private fun letPattern(): PsiElementPattern.Capture<PsiElement> =
        baseCodeStatementPattern().and(statementBeginningPattern("let"))

    private fun loopFlowCommandPattern(): PsiElementPattern.Capture<PsiElement>
        = RsPsiPattern.inAnyLoop.and(
            newCodeStatementPattern() or
            pathExpressionPattern()
        )

    private fun baseCodeStatementPattern(): PsiElementPattern.Capture<PsiElement> =
        psiElement()
            .inside(psiElement<RsFunction>())
            .andNot(psiElement().withParent(RsModItem::class.java))
            .andNot(psiElement().withSuperParent(2, RsStructLiteralBody::class.java))
            .andNot(psiElement().withSuperParent(3, RsPatStruct::class.java))

    private fun statementBeginningPattern(vararg startWords: String): PsiElementPattern.Capture<PsiElement> =
        psiElement(IDENTIFIER).and(RsPsiPattern.onStatementBeginning(*startWords))

    private fun ifElsePattern(): PsiElementPattern.Capture<PsiElement> {
        val braceAfterIf = psiElement(RBRACE).withSuperParent(2, psiElement(IF_EXPR))
        return psiElement().afterLeafSkipping(RsPsiPattern.whitespace, braceAfterIf)
    }

    private fun letElsePattern(): PsiElementPattern.Capture<PsiElement> {
        val withSemicolon = psiElement().withLastChildSkipping(RsPsiPattern.whitespace, psiElement(SEMICOLON))
        val letPattern = psiElement<RsLetDecl>().andNot(withSemicolon)
        val parent = psiElement().withPrevSiblingSkipping(RsPsiPattern.whitespace, letPattern)
        return psiElement().withSuperParent(2, parent)   // let _ = ... /*caret*/
            .or(psiElement().withSuperParent(3, parent)) // let _ = ... /*caret*/;
    }

    private fun wherePattern(): PsiElementPattern.Capture<PsiElement> {
        val typeParameters = psiElement<RsTypeParameterList>()

        val function = psiElement<RsFunction>()
            .withLastChildSkipping(RsPsiPattern.error, or(psiElement<RsValueParameterList>(), psiElement<RsRetType>()))
            .andOr(
                psiElement().withChild(psiElement<RsTypeParameterList>()),
                psiElement().withParent(RsMembers::class.java)
            )

        val struct = psiElement<RsStructItem>()
            .withChild(typeParameters)
            .withLastChildSkipping(RsPsiPattern.error, or(typeParameters, psiElement<RsTupleFields>()))

        val enum = psiElement<RsEnumItem>()
            .withLastChildSkipping(RsPsiPattern.error, typeParameters)

        val typeAlias = psiElement<RsTypeAlias>()
            .withLastChildSkipping(RsPsiPattern.error, typeParameters)
            .andNot(psiElement().withParent(RsMembers::class.java))

        val trait = psiElement<RsTraitItem>()
            .withLastChildSkipping(RsPsiPattern.error, or(psiElement(IDENTIFIER), typeParameters))

        val impl = psiElement<RsImplItem>()
            .withLastChildSkipping(RsPsiPattern.error, psiElement<RsTypeReference>())

        return psiElement()
            .withPrevSiblingSkipping(RsPsiPattern.whitespace, or(function, struct, enum, typeAlias, trait, impl))
    }

    private fun pathExpressionPattern(): PsiElementPattern.Capture<PsiElement> {
        val parent = psiElement<RsPath>()
            .with(object : PatternCondition<RsPath>("RsPath") {
                override fun accepts(t: RsPath, context: ProcessingContext?): Boolean {
                    return t.path == null && t.typeQual == null
                }
            })

        return psiElement(IDENTIFIER)
            .withParent(parent)
            .withSuperParent<RsPathExpr>(2)
            .inside(psiElement<RsFunction>())
    }

    private fun constParameterBeginningPattern(): PsiElementPattern.Capture<PsiElement> {
        val parent = psiElement<RsTypeParameter>()
            .with(object : PatternCondition<RsTypeParameter>("RsConstParameterBeginning") {
                override fun accepts(t: RsTypeParameter, context: ProcessingContext?): Boolean {
                    val leftSibling = t.leftSiblings.firstOrNull { it !is PsiWhiteSpace }
                    if (leftSibling != null && leftSibling.elementType != LT && leftSibling.elementType != COMMA) {
                        return false
                    }

                    val rightSibling = t.rightSiblings.firstOrNull { it is RsElement }
                    if (rightSibling is RsTypeParameter || rightSibling is RsLifetimeParameter) {
                        return false
                    }

                    return true
                }
            })

        return psiElement(IDENTIFIER).withParent(parent)
    }

    private fun traitOrImplDeclarationPattern(): PsiElementPattern.Capture<PsiElement> {
        return baseTraitOrImplDeclaration().and(statementBeginningPattern())
    }

    private fun unsafeTraitOrImplDeclarationPattern(): PsiElementPattern.Capture<PsiElement> {
        return baseTraitOrImplDeclaration().and(statementBeginningPattern("unsafe"))
    }

    private fun afterVisInherentImplDeclarationPattern(): PsiElementPattern.Capture<PsiElement> {
        return baseInherentImplDeclarationPattern().and(afterVis())
    }

    // TODO(parser recovery?): it would be really nice to just say something like element.prevSibling is RsVis
    private fun afterVis(): PsiElementPattern.Capture<PsiElement> = psiElement().with("afterVis") { item, _ ->
        val siblings = generateSequence(item) { it.prevSibling }.takeWhile {
            it is RsPath || it.elementType in RS_VIS_ALLOWED_TOKENS
        }.filter {
            it !is PsiWhiteSpace && it !is PsiComment
        }

        siblings.lastOrNull()?.elementType == PUB
    }

    private fun elseLookupElement() = LookupElementBuilder
        .create("else")
        .bold()
        .withTailText(" {...}")
        .withInsertHandler { ctx, _ ->
            ctx.document.insertString(ctx.selectionEndOffset, " {  }")
            EditorModificationUtil.moveCaretRelatively(ctx.editor, 3)
        }

    companion object {
        @JvmField
        val CONDITION_KEYWORDS: List<String> = listOf("if", "match")

        val RS_VIS_ALLOWED_TOKENS = TokenSet.orSet(
            tokenSetOf(
                PUB,
                LPAREN,
                SUPER,
                CRATE,
                SELF,
                COLONCOLON,
                IN,
                PATH,
                IDENTIFIER,
                RPAREN,
                TokenType.WHITE_SPACE,
                TokenType.ERROR_ELEMENT
            ),
            RS_COMMENTS
        )
    }
}
