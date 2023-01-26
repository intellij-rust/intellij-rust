/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.ide.inspections.fixes.RemoveImportFix
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.macros.proc.ProcMacroApplicationService
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.*
import org.rust.openapiext.isUnitTestMode
import javax.swing.JComponent

class RsUnusedImportInspection : RsLintInspection() {

    var ignoreDoctest: Boolean = true
    var enableOnlyIfProcMacrosEnabled: Boolean = true

    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedImports

    override fun getShortName(): String = SHORT_NAME

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun visitUseItem2(item: RsUseItem) {
            if (!isApplicableForUseItem(item)) return

            // It's common to include more imports than needed in doctest sample code
            if (ignoreDoctest && item.containingCrate is DoctestCrate) return
            if (enableOnlyIfProcMacrosEnabled && !ProcMacroApplicationService.isFullyEnabled() && !isUnitTestMode) return

            val owner = item.parent as? RsItemsOwner ?: return
            val usage = owner.pathUsage

            val speck = item.useSpeck ?: return

            val unusedSpeckSet = HashSet<RsUseSpeck>()
            storeUseSpeckUsage(speck, usage, unusedSpeckSet)
            markUnusedSpecks(speck, unusedSpeckSet, holder)
        }
    }

    /**
     * Traverse all use specks recursively and store information about their usage.
     */
    private fun storeUseSpeckUsage(
        useSpeck: RsUseSpeck,
        usage: PathUsageMap,
        unusedSpeckSet: MutableSet<RsUseSpeck>
    ) {
        val group = useSpeck.useGroup
        val isUsed = if (group == null) {
            isUseSpeckUsed(useSpeck, usage)
        } else {
            group.useSpeckList.forEach {
                storeUseSpeckUsage(it, usage, unusedSpeckSet)
            }
            group.useSpeckList.any { it !in unusedSpeckSet }
        }
        if (!isUsed) {
            unusedSpeckSet.add(useSpeck)
        }
    }

    private fun markUnusedSpecks(
        useSpeck: RsUseSpeck,
        unusedSpeckSet: Set<RsUseSpeck>,
        holder: RsProblemsHolder
    ) {
        val used = useSpeck !in unusedSpeckSet

        if (!used) {
            markAsUnused(useSpeck, holder)
        } else {
            useSpeck.useGroup?.useSpeckList?.forEach {
                markUnusedSpecks(it, unusedSpeckSet, holder)
            }
        }
    }

    private fun markAsUnused(useSpeck: RsUseSpeck, holder: RsProblemsHolder) {
        val element = getHighlightElement(useSpeck)
        // https://github.com/intellij-rust/intellij-rust/issues/7565
        val fixes = if (useSpeck.isDoctestInjection) emptyList() else listOf((RemoveImportFix(element)))
        holder.registerLintProblem(
            element,
            "Unused import: `${useSpeck.text}`",
            RsLintHighlightingType.UNUSED_SYMBOL,
            fixes
        )
    }

    override fun createOptionsPanel(): JComponent = MultipleCheckboxOptionsPanel(this).apply {
        addCheckbox("Ignore unused imports in doctests", "ignoreDoctest")
        addCheckbox("Enable inspection only if procedural macros are enabled", "enableOnlyIfProcMacrosEnabled")
    }

    companion object {
        fun isEnabled(project: Project): Boolean {
            val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
            return profile.isToolEnabled(HighlightDisplayKey.find(SHORT_NAME))
                && checkProcMacrosMatch(profile, project)
        }

        private fun checkProcMacrosMatch(profile: InspectionProfileImpl, project: Project): Boolean {
            if (isUnitTestMode) return true
            val toolWrapper = profile.getInspectionTool(SHORT_NAME, project)
            val tool = toolWrapper?.tool as? RsUnusedImportInspection ?: return true
            if (!tool.enableOnlyIfProcMacrosEnabled) return true
            return ProcMacroApplicationService.isFullyEnabled()
        }

        const val SHORT_NAME: String = "RsUnusedImport"
    }
}

private fun getHighlightElement(useSpeck: RsUseSpeck): PsiElement {
    if (useSpeck.parent is RsUseItem) {
        return useSpeck.parent
    }
    return useSpeck
}

private fun isApplicableForUseItem(item: RsUseItem): Boolean {
    val crate = item.containingCrate
    if (item.visibility == RsVisibility.Public && crate.kind.isLib) return false
    if (!item.existsAfterExpansion(crate)) return false
    return true
}

/**
 * Returns true if this use speck has at least one usage in its containing owner (module or function).
 * A usage can be either a path that uses the import of the use speck or a method call/associated item available through
 * a trait that is imported by this use speck.
 */
fun RsUseSpeck.isUsed(pathUsage: PathUsageMap): Boolean {
    val useItem = ancestorStrict<RsUseItem>() ?: return true
    if (!isApplicableForUseItem(useItem)) return true
    return isUseSpeckUsed(this, pathUsage)
        || RsLint.UnusedImports.explicitLevel(useItem) == RsLintLevel.ALLOW
}

private fun isUseSpeckUsed(useSpeck: RsUseSpeck, usage: PathUsageMap): Boolean {
    if (useSpeck.path?.resolveStatus != PathResolveStatus.RESOLVED) return true

    val items = if (useSpeck.isStarImport) {
        val module = useSpeck.path?.reference?.resolve() as? RsMod ?: return true
        module.exportedItems(useSpeck.containingMod)
    } else {
        val path = useSpeck.path ?: return true
        val items = path.reference?.multiResolve() ?: return true
        val name = useSpeck.itemName(withAlias = true) ?: return true
        items.filterIsInstance<RsNamedElement>().map { NamedItem(name, it) }
    }

    return items.any { (importName, item) ->
        isItemUsedInSameMod(item, importName, usage) || isItemUsedInOtherMods(item, importName, useSpeck)
    }
}

private fun isItemUsedInSameMod(item: RsNamedElement, importName: String, usage: PathUsageMap): Boolean {
    val used = item in usage.pathUsages[importName].orEmpty()
        || item in usage.traitUsages
    val probablyUsed = importName in usage.unresolvedPaths
        || (item as? RsTraitItem)?.expandedMembers.orEmpty().any { it.name in usage.unresolvedMethods }
    return used || probablyUsed
}

private fun isItemUsedInOtherMods(item: RsNamedElement, importName: String, useSpeck: RsUseSpeck): Boolean {
    val useItem = useSpeck.ancestorStrict<RsUseItem>() ?: return true
    val importMod = useItem.containingMod
    val useItemVisibility = when (val visibility = useItem.visibility) {
        RsVisibility.Private -> {
            if (!importMod.hasChildModules) return false
            RsVisibility.Restricted(importMod)
        }
        is RsVisibility.Restricted -> visibility
        // we handle public imports in binary crates only, so this is effectively `pub(crate)` import
        RsVisibility.Public -> RsVisibility.Restricted(importMod.crateRoot ?: return true)
    }
    if (item is RsTraitItem) {
        // TODO we should search usages for all methods of the trait
        return true
    }

    val searchScope = RsPsiImplUtil.getDeclarationUseScope(useItem)
    return !item.processReferencesWithAliases(searchScope, importName) { element ->
        !isImportNeededForReference(element, useItemVisibility, importMod)
    }
}

private fun isImportNeededForReference(
    reference: RsReferenceElement,
    importVisibility: RsVisibility.Restricted,
    importMod: RsMod
): Boolean {
    if (reference.containingMod == importMod) {
        // References from same mod are already checked
        // Note that this check should be performed after `findExpansionElements`
        return false
    }
    if (importVisibility.inMod !in reference.containingMod.superMods) {
        // We should check references only from mods inside use item visibility scope
        return false
    }

    return when (reference) {
        is RsPath -> {
            val qualifier = reference.qualifier
            if (qualifier == null) {
                reference.containingMod.hasTransitiveGlobImportTo(importMod)
            } else {
                val qualifierTarget = qualifier.reference?.resolve() as? RsMod ?: return false
                qualifierTarget.hasTransitiveGlobImportTo(importMod)
            }
        }
        is RsPatBinding -> {
            reference.containingMod.hasTransitiveGlobImportTo(importMod)
        }
        else -> false
    }
}

private fun RsMod.hasTransitiveGlobImportTo(target: RsMod): Boolean {
    if (this == target) return true
    val crateId = containingCrate.asNotFake?.id ?: return false
    if (crateId != target.containingCrate.id) {
        // we consider `pub` imports as always used,
        // so any possible usage of import will be in same crate
        return false
    }
    val defMap = project.defMapService.getOrUpdateIfNeeded(crateId) ?: return false
    return defMap.hasTransitiveGlobImport(this, target)
}

/**
 * Like [RsElement.searchReferences], but takes into account reexports with aliases.
 * Returns `true` if [originalProcessor] returns `true` for all references
 */
fun RsElement.processReferencesWithAliases(
    searchScope: SearchScope?,
    identifier: String,
    originalProcessor: (RsReferenceElement) -> Boolean
): Boolean {
    // returning `false` stops the processing
    fun processor(element: PsiElement): Boolean {
        if (element !is RsReferenceElementBase || element.referenceName != identifier) return true
        element.findExpansionElements()?.let { expansionElements ->
            return expansionElements
                .mapNotNull { it.ancestorStrict<RsElement>() }  // PsiElement(identifier)
                .all(::processor)
        }
        return if (element is RsReferenceElement && element.reference?.isReferenceTo(this) == true) {
            originalProcessor(element)
        } else {
            true
        }
    }
    return PsiSearchHelper.getInstance(project).processElementsWithWord(
        { element, _ -> processor(element) },
        searchScope ?: GlobalSearchScope.allScope(project),
        identifier,
        UsageSearchContext.IN_CODE,
        true
    )
}
