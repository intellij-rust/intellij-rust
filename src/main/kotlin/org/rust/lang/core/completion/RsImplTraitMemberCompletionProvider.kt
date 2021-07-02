/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.rust.ide.presentation.ImportingPsiRenderer
import org.rust.ide.presentation.PsiRenderingOptions
import org.rust.ide.presentation.renderFunctionSignature
import org.rust.ide.presentation.renderTypeReference
import org.rust.ide.utils.import.import
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.block
import org.rust.lang.core.psi.ext.expandedMembers
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.RsPsiSubstitution
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.type
import org.rust.openapiext.selectElement

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

        for (item in parentItems) {
            val lookup = getCompletion(item, trait, implBlock, subst) ?: continue
            result.addElement(
                lookup.withPriority(KEYWORD_PRIORITY + 1)
            )
        }
    }
}

private fun getCompletion(
    target: RsAbstractable,
    trait: BoundElement<RsTraitItem>,
    impl: RsImplItem,
    substitution: RsPsiSubstitution
): LookupElementBuilder? {
    return when (target) {
        is RsConstant -> completeConstant(target, trait, impl, substitution)
        is RsTypeAlias -> completeType(target)
        is RsFunction -> completeFunction(target, impl, substitution)
        else -> error("unreachable")
    }
}

private fun completeConstant(
    target: RsConstant,
    trait: BoundElement<RsTraitItem>,
    impl: RsImplItem,
    substitution: RsPsiSubstitution
): LookupElementBuilder? {
    val renderer = ImportingPsiRenderer(PsiRenderingOptions(renderGenericsAndWhere = true), substitution, impl)
    val text = buildString {
        append("const ")
        append(target.name)
        val typeRef = target.typeReference ?: return null
        append(": ")
        append(renderer.renderTypeReference(typeRef))
        val builder = RsDefaultValueBuilder(impl.knownItems, target.containingMod, RsPsiFactory(target.project))
        append(" = ")
        val type = typeRef.type.substitute(trait.subst)
        append(builder.buildFor(type, mapOf()).text)
        append(';')
    }
    return LookupElementBuilder.create(text)
        .withIcon(target.getIcon(0))
        .withInsertHandler { context, _ ->
            val element = context.getElementOfType<RsConstant>() ?: return@withInsertHandler
            for (importCandidate in renderer.itemsToImport) {
                importCandidate.import(impl)
            }
            val expr = element.expr ?: return@withInsertHandler
            selectElement(expr, context.editor)
        }
}

private fun completeType(target: RsTypeAlias): LookupElementBuilder {
    val text = buildString {
        append("type ")
        append(target.name)
        append(" = ();")
    }
    return LookupElementBuilder.create(text)
        .withIcon(target.getIcon(0))
        .withInsertHandler { context, _ ->
            val element = context.getElementOfType<RsTypeAlias>() ?: return@withInsertHandler
            val typeReference = element.typeReference ?: return@withInsertHandler
            selectElement(typeReference, context.editor)
        }
}

private fun completeFunction(
    target: RsFunction,
    impl: RsImplItem,
    substitution: RsPsiSubstitution
): LookupElementBuilder {
    val renderer = ImportingPsiRenderer(PsiRenderingOptions(renderGenericsAndWhere = true), substitution, impl)
    val shortRenderer = ImportingPsiRenderer(PsiRenderingOptions(renderGenericsAndWhere = false), substitution, impl)
    val shortSignature = shortRenderer.renderFunctionSignature(target)
    var signature = renderer.renderFunctionSignature(target)
    if (!signature.endsWith(" ")) {
        signature += " "
    }
    val text = "$signature{\n        todo!()\n    }"
    return LookupElementBuilder
        .create(text)
        .withIcon(target.getIcon(0))
        .withInsertHandler { context, _ ->
            val element = context.getElementOfType<RsFunction>() ?: return@withInsertHandler
            for (importCandidate in renderer.itemsToImport) {
                importCandidate.import(impl)
            }
            val body = element.block?.expr ?: return@withInsertHandler
            selectElement(body, context.editor)
        }
        .withPresentableText("$shortSignature { ... }")
}
