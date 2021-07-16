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
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.rust.ide.presentation.PsiRenderingOptions
import org.rust.ide.presentation.PsiSubstitutingPsiRenderer
import org.rust.ide.presentation.renderFunctionSignature
import org.rust.ide.refactoring.implementMembers.MembersGenerator
import org.rust.ide.utils.import.import
import org.rust.ide.utils.template.buildAndRunTemplate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.psi.ext.expandedMembers
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.types.RsPsiSubstitution
import org.rust.openapiext.createSmartPointer

object RsImplTraitMemberCompletionProvider : RsCompletionProvider() {
    override val elementPattern: ElementPattern<PsiElement>
        get() {
            val contributor = RsKeywordCompletionContributor()
            return contributor.traitOrImplDeclarationPattern()
        }

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

        val memberGenerator = MembersGenerator(RsPsiFactory(element.project), implBlock, trait)
        for (item in parentItems) {
            val lookup = getCompletion(item, implBlock, subst, memberGenerator)
            result.addElement(
                lookup.withPriority(KEYWORD_PRIORITY + 1)
            )
        }
    }
}

private fun getCompletion(
    target: RsAbstractable,
    impl: RsImplItem,
    substitution: RsPsiSubstitution,
    memberGenerator: MembersGenerator
): LookupElementBuilder {
    return when (target) {
        is RsConstant -> completeConstant(target, impl, memberGenerator)
        is RsTypeAlias -> completeType(target, memberGenerator)
        is RsFunction -> completeFunction(target, impl, substitution, memberGenerator)
        else -> error("unreachable")
    }
}

private fun completeConstant(
    target: RsConstant,
    impl: RsImplItem,
    memberGenerator: MembersGenerator
): LookupElementBuilder {
    val text = memberGenerator.renderAbstractable(target)
    return LookupElementBuilder.create(text)
        .withIcon(target.getIcon(0))
        .withInsertHandler { context, _ ->
            val element = context.getElementOfType<RsConstant>() ?: return@withInsertHandler
            for (importCandidate in memberGenerator.itemsToImport) {
                importCandidate.import(impl)
            }
            val expr = element.expr ?: return@withInsertHandler
            runTemplate(expr, context.editor)
        }
}

private fun completeType(target: RsTypeAlias, memberGenerator: MembersGenerator): LookupElementBuilder {
    val text = memberGenerator.renderAbstractable(target)
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
    memberGenerator: MembersGenerator
): LookupElementBuilder {
    val shortRenderer = PsiSubstitutingPsiRenderer(PsiRenderingOptions(renderGenericsAndWhere = false), substitution)
    val shortSignature = shortRenderer.renderFunctionSignature(target)
    val text = memberGenerator.renderAbstractable(target)
    return LookupElementBuilder
        .create(text)
        .withIcon(target.getIcon(0))
        .withInsertHandler { context, _ ->
            val element = context.getElementOfType<RsFunction>() ?: return@withInsertHandler
            for (importCandidate in memberGenerator.itemsToImport) {
                importCandidate.import(impl)
            }
            val body = element.block?.expr ?: return@withInsertHandler
            runTemplate(body, context.editor)
        }
        .withPresentableText("$shortSignature { ... }")
}

private fun runTemplate(element: RsElement, editor: Editor) {
    editor.buildAndRunTemplate(element.parent, listOf(element.createSmartPointer()))
}
