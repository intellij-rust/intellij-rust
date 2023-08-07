/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.hints.codeVision.InheritorsCodeVisionProvider
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import org.rust.RsBundle
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.rustStructureModificationTracker
import org.rust.openapiext.isUnitTestMode
import java.awt.event.MouseEvent

class RsImplementationsCodeVisionProvider : InheritorsCodeVisionProvider() {
    override fun acceptsFile(file: PsiFile): Boolean = file is RsFile

    override fun acceptsElement(element: PsiElement): Boolean {
        if (!isUnitTestMode && !Registry.`is`("org.rust.code.vision.implementation", false)) return false

        return when (element) {
            is RsTraitItem -> true
            is RsAbstractable -> element.owner is RsAbstractableOwner.Trait
            else -> false
        }
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        // The following check is required to smart cast `element` to `RsElement`
        if (element !is RsElement) return null
        if (element !is RsTraitItem && element !is RsAbstractable) return null

        val implementationCount = element.implementationCount
        if (implementationCount == 0) return null

        return when (element) {
            is RsTraitItem -> {
                return RsBundle.message("rust.code.vision.implementation.hint", implementationCount)
            }

            is RsAbstractable -> {
                return if (element.isAbstract) {
                    RsBundle.message("rust.code.vision.implementation.hint", implementationCount)
                } else {
                    RsBundle.message("rust.code.vision.overrides.hint", implementationCount)
                }
            }

            else -> null
        }
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        // copied from ImplsNavigationGutterIconRenderer::navigateToItems
        if (event == null) return

        val namedElement = element as? RsNamedElement ?: return
        val elementName = namedElement.name ?: return

        val targets = getImplementations(element).filterIsInstance<NavigatablePsiElement>().toTypedArray()
        if (targets.isEmpty()) return

        val escapedName = StringUtil.escapeXmlEntities(elementName)

        PsiElementListNavigator.openTargets(
            event,
            targets,
            CodeInsightBundle.message("goto.implementation.chooserTitle", escapedName, targets.size, ""),
            CodeInsightBundle.message("goto.implementation.findUsages.title", escapedName, targets.size),
            DefaultPsiElementCellRenderer()
        )
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter(RsReferenceCodeVisionProvider.ID))

    override val id: String = ID

    companion object {
        const val ID = "rust.inheritors"
    }
}

private fun getImplementationsQuery(element: PsiElement): Query<out PsiElement> {
    return when (element) {
        is RsTraitItem -> element.searchForImplementations()
        is RsAbstractable -> element.searchForImplementations()
        else -> EmptyQuery()
    }
}

private fun countImplementations(element: PsiElement): Int = getImplementations(element).size

private fun getImplementations(element: PsiElement): List<PsiElement> =
    getImplementationsQuery(element).findAll().toList()

private val IMPL_CACHE_KEY: Key<CachedValue<Int>> = Key.create("IMPL_CACHE_KEY")

private val RsElement.implementationCount: Int
    get() = CachedValuesManager.getCachedValue(this, IMPL_CACHE_KEY) {
        val usages = countImplementations(this)
        CachedValueProvider.Result.create(usages, project.rustStructureModificationTracker)
    }
