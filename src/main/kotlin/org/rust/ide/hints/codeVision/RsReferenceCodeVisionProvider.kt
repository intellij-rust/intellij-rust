/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import org.rust.RsBundle
import org.rust.ide.statistics.RsCodeVisionUsageCollector.Companion.logUsagesClicked
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsReferenceCodeVisionProvider : ReferencesCodeVisionProvider() {
    override fun acceptsFile(file: PsiFile): Boolean = file is RsFile

    override fun acceptsElement(element: PsiElement): Boolean {
        if (!CODE_VISION_USAGE_KEY.asBoolean()) return false

        return when (element) {
            // Searching references for an associated item (e.g. for a method) may require
            // to infer types in many functions which can be arbitrary slow
            is RsAbstractable -> if (element.ownerBySyntaxOnly.isImplOrTrait) {
                CODE_VISION_USAGE_SLOW_KEY.asBoolean()
            } else {
                true
            }

            // Searching references for a field may require
            // to infer types in many functions which can be arbitrary slow
            is RsNamedFieldDecl,
                // Searching references for an enum variant may require `ImplLookup` instantiation
                // and a type normalization which can be slow in some cases
            is RsEnumVariant,
                // `RsMacro` and `RsMacro2` need a proper `getUseScope()` implementation
            is RsMacroDefinitionBase -> CODE_VISION_USAGE_SLOW_KEY.asBoolean()

            is RsStructOrEnumItemElement,
            is RsTraitItem,
            is RsTraitAlias,
            is RsModItem,
            is RsModDeclItem -> true

            else -> false
        }
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val namedElement: RsNamedElement = when (element) {
            is RsModDeclItem -> element.reference.resolve() as? RsFile ?: return null
            is RsNamedElement -> element
            else -> return null
        }
        val name = namedElement.name ?: return null

        val searchHelper = PsiSearchHelper.getInstance(namedElement.project)
        val useScope = searchHelper.getUseScope(namedElement)
        if (useScope is GlobalSearchScope) {
            val searchCost = searchHelper.isCheapEnoughToSearch(name, useScope, file, null)
            if (searchCost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
                // Don't show the usages count in case of too many occurrences
                return null
            }
        }

        var usageCount = namedElement.searchReferences(useScope).count()

        if (element is RsModDeclItem && usageCount > 0) {
            // `mod foo;` is itself a usage of the mod, so let's subtract it
            usageCount--
        }

        if (usageCount == 0) return null

        return RsBundle.message("rust.code.vision.usage.hint", usageCount)
    }

    override fun logClickToFUS(element: PsiElement, hint: String) {
        logUsagesClicked(element)
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    override val id: String = ID

    companion object {
        const val ID = "rust.references"
    }
}

private val CODE_VISION_USAGE_KEY: RegistryValue = Registry.get("org.rust.code.vision.usage")
private val CODE_VISION_USAGE_SLOW_KEY: RegistryValue = Registry.get("org.rust.code.vision.usage.slow")
