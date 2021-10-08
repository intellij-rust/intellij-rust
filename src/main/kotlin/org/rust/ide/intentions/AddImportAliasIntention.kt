/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.lints.PathUsageBase
import org.rust.ide.inspections.lints.RsUnusedImportInspection
import org.rust.ide.inspections.lints.traversePathUsages
import org.rust.ide.refactoring.isValidRustVariableIdentifier
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUseItem
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.createSmartPointer

class AddImportAliasIntention : RsElementBaseIntentionAction<AddImportAliasIntention.Context>() {
    override fun getText(): String = "Add import alias"
    override fun getFamilyName() = text

    data class Context(val speck: RsUseSpeck, val name: String)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val speck = element.ancestorStrict<RsUseSpeck>() ?: return null
        if (speck.useGroup != null || speck.alias != null || speck.isStarImport) return null

        val name = speck.path?.referenceName ?: return null
        if (!isValidRustVariableIdentifier(name)) return null

        val useItem = speck.ancestorStrict<RsUseItem>() ?: return null
        if (
            !RsUnusedImportInspection.isEnabled(useItem.project) ||
            !RsUnusedImportInspection.isApplicableForUseItem(useItem)
        ) {
            return null
        }

        return Context(speck, name)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val speck = ctx.speck
        val name = ctx.name

        val targets = speck.path?.reference?.multiResolve()?.toSet() ?: return

        // Original elements have to be gathered before the alias is introduced
        val toReplace = getElementsToReplace(name, speck, targets).map { it.createSmartPointer() }

        val factory = RsPsiFactory(project)
        val speckWithAlias = factory.createUseSpeck("${speck.text} as T")

        val inserted = speck.replace(speckWithAlias) as RsUseSpeck

        val template = editor.newTemplateBuilder(inserted.containingMod) ?: return
        val alias = inserted.alias?.identifier ?: return

        template.introduceVariable(alias, "").apply {
            toReplace.forEach {
                replaceElementWithVariable(it.element ?: return@forEach)
            }
        }
        template.runInline()
    }
}

private fun getElementsToReplace(name: String, speck: RsUseSpeck, targets: Set<RsElement>): Set<PsiElement> {
    val owner = speck.ancestorStrict<RsItemsOwner>() ?: return emptySet()
    val elements = HashSet<PsiElement>()

    traversePathUsages(owner) { usage ->
        if (
            usage is PathUsageBase.PathUsage &&
            usage.name == name &&
            usage.targets.intersect(targets).isNotEmpty() &&
            usage.source != null &&
            speck !in usage.source.ancestors
        ) {
            elements.add(usage.source)
        }
    }

    return elements
}
