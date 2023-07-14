/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType.ERROR_ELEMENT
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.rust.ide.presentation.PsiRenderingOptions
import org.rust.ide.presentation.PsiSubstitutingPsiRenderer
import org.rust.ide.presentation.renderFunctionSignature
import org.rust.ide.refactoring.implementMembers.MembersGenerator
import org.rust.ide.utils.import.import
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.impl.RsConstantImpl
import org.rust.lang.core.psi.impl.RsFunctionImpl
import org.rust.lang.core.psi.impl.RsTypeAliasImpl
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.types.RsPsiSubstitution
import org.rust.openapiext.createSmartPointer

object RsImplTraitMemberCompletionProvider : RsCompletionProvider() {
    private val KEYWORD_TO_ABSTRACTABLE = mapOf(
        FN to RsFunctionImpl::class.java,
        CONST to RsConstantImpl::class.java,
        TYPE_KW to RsTypeAliasImpl::class.java
    )
    private val KEYWORD_TOKEN_TYPES = TokenSet.orSet(tokenSetOf(FN, CONST, TYPE_KW, WHITE_SPACE, ERROR_ELEMENT), RS_COMMENTS)

    override val elementPattern: PsiElementPattern.Capture<PsiElement> = RsPsiPattern.baseTraitOrImplDeclaration()

    private val withoutPrefixPattern: ElementPattern<PsiElement> = elementPattern.and(RsPsiPattern.onStatementBeginning)

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val implBlock = element.parentOfType<RsImplItem>() ?: return

        val traitRef = implBlock.traitRef ?: return

        val trait = implBlock.implementedTrait ?: return
        val subst = pathPsiSubst(traitRef.path, trait.element)

        val members = trait.element.members ?: return

        val parentItems = members.expandedMembers.toMutableSet()
        for (item in implBlock.expandedMembers) {
            parentItems.removeIf { it.javaClass == item.javaClass && it.name == item.name }
        }

        // Look for fn/const/type before the caret and filter out the corresponding items
        val keyword = getPreviousKeyword(element)?.let {
            val abstractable = KEYWORD_TO_ABSTRACTABLE[it.elementType] ?: return@let null
            it to abstractable
        }
        if (keyword != null) {
            parentItems.removeIf { it.javaClass != keyword.second }
        } else if (!withoutPrefixPattern.accepts(element)) {
            return
        }

        for (item in parentItems) {
            // have to create new generator for each item, so that [MembersGenerator.itemsToImport] don't mix up
            val memberGenerator = MembersGenerator(RsPsiFactory(element.project), implBlock, trait)
            val lookup = getCompletion(item, implBlock, subst, memberGenerator, keyword?.first)
            result.addElement(
                lookup.toRsLookupElement(RsLookupElementProperties(isFullLineCompletion = true))
            )
        }
    }

    private fun getPreviousKeyword(element: PsiElement): PsiElement? {
        return generateSequence(element.prevSibling) { it.prevSibling }
            .takeWhile {
                it.elementType in KEYWORD_TOKEN_TYPES
            }
            .filter { it.elementType in KEYWORD_TO_ABSTRACTABLE.keys }
            .firstOrNull()
    }
}

private fun getCompletion(
    target: RsAbstractable,
    impl: RsImplItem,
    substitution: RsPsiSubstitution,
    memberGenerator: MembersGenerator,
    keyword: PsiElement?
): LookupElementBuilder {
    return when (target) {
        is RsConstant -> completeConstant(target, impl, memberGenerator, keyword)
        is RsTypeAlias -> completeType(target, memberGenerator, keyword)
        is RsFunction -> completeFunction(target, impl, substitution, memberGenerator, keyword)
        else -> error("unreachable")
    }
}

private fun completeConstant(
    target: RsConstant,
    impl: RsImplItem,
    memberGenerator: MembersGenerator,
    keyword: PsiElement?
): LookupElementBuilder {
    val text = removePrefix(memberGenerator.renderAbstractable(target), keyword)

    return LookupElementBuilder.create(text)
        .withIcon(target.getIcon(0))
        .withInsertHandler { context, _ ->
            val element = context.getElementOfType<RsConstant>() ?: return@withInsertHandler
            for (importCandidate in memberGenerator.itemsToImport) {
                importCandidate.import(impl)
            }

            val reformatted = reformat(element) ?: return@withInsertHandler

            val expr = reformatted.expr ?: return@withInsertHandler
            runTemplate(expr, context.editor)
        }
}

private fun completeType(
    target: RsTypeAlias,
    memberGenerator: MembersGenerator,
    keyword: PsiElement?
): LookupElementBuilder {
    val text = removePrefix(memberGenerator.renderAbstractable(target), keyword)
    return LookupElementBuilder.create(text)
        .withIcon(target.getIcon(0))
        .withInsertHandler { context, _ ->
            val element = context.getElementOfType<RsTypeAlias>() ?: return@withInsertHandler
            val typeReference = element.typeReference ?: return@withInsertHandler
            runTemplate(typeReference, context.editor)
        }
}

private fun completeFunction(
    target: RsFunction,
    impl: RsImplItem,
    substitution: RsPsiSubstitution,
    memberGenerator: MembersGenerator,
    keyword: PsiElement?
): LookupElementBuilder {
    val shortRenderer = PsiSubstitutingPsiRenderer(PsiRenderingOptions(renderGenericsAndWhere = false), listOf(substitution))
    val shortSignature = removePrefix(shortRenderer.renderFunctionSignature(target), keyword)
    val text = removePrefix(memberGenerator.renderAbstractable(target), keyword)

    return LookupElementBuilder
        .create(text)
        .withIcon(target.getIcon(0))
        .withInsertHandler { context, _ ->
            val element = context.getElementOfType<RsFunction>() ?: return@withInsertHandler
            for (importCandidate in memberGenerator.itemsToImport) {
                importCandidate.import(impl)
            }
            val reformatted = reformat(element) ?: return@withInsertHandler

            val body = reformatted.block?.syntaxTailStmt ?: return@withInsertHandler
            runTemplate(body, context.editor)
        }
        .withPresentableText("$shortSignature { ... }")
}

private fun <T: RsElement>reformat(element: T): T? {
    val ptr = element.createSmartPointer()
    CodeStyleManager.getInstance(element.project).reformatText(
        element.containingFile,
        element.startOffset,
        element.endOffset
    )
    return ptr.element
}

private fun runTemplate(element: RsElement, editor: Editor) {
    editor.buildAndRunTemplate(element.parent, listOf(element))
}

private fun removePrefix(text: String, keyword: PsiElement?): String {
    return if (keyword != null) {
        text.removePrefix(keyword.text).trimStart()
    } else {
        text
    }
}
