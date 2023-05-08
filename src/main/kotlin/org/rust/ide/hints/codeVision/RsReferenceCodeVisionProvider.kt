/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import org.rust.RsBundle
import org.rust.ide.statistics.RsCodeVisionUsageCollector.Companion.logUsagesClicked
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMacroDefinitionBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.searchReferences
import org.rust.openapiext.isUnitTestMode

class RsReferenceCodeVisionProvider : ReferencesCodeVisionProvider() {
    override fun acceptsFile(file: PsiFile): Boolean = file is RsFile

    override fun acceptsElement(element: PsiElement): Boolean {
        if (!isUnitTestMode && !Registry.`is`("org.rust.code.vision.usage", false)) return false

        return when (element) {
            is RsFunction,
            is RsStructOrEnumItemElement,
            is RsEnumVariant,
            is RsNamedFieldDecl,
            is RsTraitItem,
            is RsTypeAlias,
            is RsConstant,
            is RsMacroDefinitionBase,
            is RsModItem -> true
            else -> false
        }
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val namedElement = element as? RsNamedElement ?: return null
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

        val usageCount = namedElement.searchReferences(useScope).count()
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
