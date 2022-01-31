/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.rust.RsBundle
import org.rust.ide.presentation.getPresentation
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.namespaces
import org.rust.stdext.intersects

class RsChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {
    override fun findUsages(changeInfo: ChangeInfo?): Array<UsageInfo> {
        if (changeInfo !is RsSignatureChangeInfo) return emptyArray()
        val function = changeInfo.config.function
        val usages = findFunctionUsages(function).toMutableList()
        if (function.owner is RsAbstractableOwner.Trait) {
            function.searchForImplementations().filterIsInstance<RsFunction>().forEach { method ->
                usages.add(RsFunctionUsage.MethodImplementation(method))
                usages.addAll(findFunctionUsages(method))
            }
        }

        return usages.toTypedArray()
    }

    override fun findConflicts(changeInfo: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>): MultiMap<PsiElement, String> {
        if (changeInfo !is RsSignatureChangeInfo) return MultiMap.empty()
        val map = MultiMap<PsiElement, String>()
        val config = changeInfo.config
        val function = config.function

        findNameConflicts(function, config, map)
        findVisibilityConflicts(function, config, refUsages.get(), map)

        return map
    }

    override fun processUsage(
        changeInfo: ChangeInfo?,
        usageInfo: UsageInfo?,
        beforeMethodChange: Boolean,
        usages: Array<out UsageInfo>?
    ): Boolean {
        if (beforeMethodChange) return false

        if (changeInfo !is RsSignatureChangeInfo) return false
        if (usageInfo !is RsFunctionUsage) return false
        val config = changeInfo.config
        if (usageInfo is RsFunctionUsage.MethodImplementation) {
            processFunction(config.function.project, config, usageInfo.overriddenMethod, true)
        } else {
            processFunctionUsage(config, usageInfo)
        }

        return true
    }

    override fun processPrimaryMethod(changeInfo: ChangeInfo?): Boolean {
        if (changeInfo !is RsSignatureChangeInfo) return false
        val config = changeInfo.config
        val function = config.function
        val project = function.project

        processFunction(project, config, function, changeInfo.changeSignature)
        return true
    }

    override fun shouldPreviewUsages(changeInfo: ChangeInfo?, usages: Array<out UsageInfo>?): Boolean = false

    override fun setupDefaultValues(
        changeInfo: ChangeInfo?,
        refUsages: Ref<Array<UsageInfo>>?,
        project: Project?
    ): Boolean {
        return true
    }

    override fun registerConflictResolvers(
        snapshots: MutableList<in ResolveSnapshotProvider.ResolveSnapshot>,
        resolveSnapshotProvider: ResolveSnapshotProvider,
        usages: Array<out UsageInfo>,
        changeInfo: ChangeInfo?
    ) {}
}

private fun findVisibilityConflicts(
    function: RsFunction,
    config: RsChangeFunctionSignatureConfig,
    usages: Array<UsageInfo>,
    map: MultiMap<PsiElement, String>
) {
    val functionUsages = usages.filterIsInstance<RsFunctionUsage>()
    val clone = function.copy() as RsFunction
    changeVisibility(clone, config)

    for (usage in functionUsages) {
        val sourceModule = usage.element.containingMod
        if (!clone.isVisibleFrom(sourceModule)) {
            val moduleName = sourceModule.qualifiedName.orEmpty()
            map.putValue(usage.element, RsBundle.message("refactoring.change.signature.visibility.conflict", moduleName))
        }
    }
}

private fun findNameConflicts(
    function: RsFunction,
    config: RsChangeFunctionSignatureConfig,
    map: MultiMap<PsiElement, String>
) {
    val (owner, items) = when (val owner = function.owner) {
        is RsAbstractableOwner.Impl -> owner.impl to owner.impl.expandedMembers
        is RsAbstractableOwner.Trait -> owner.trait to owner.trait.expandedMembers
        else -> {
            val parent = function.parent as RsItemsOwner
            val items = parent.expandedItemsCached.named[config.name] ?: return
            parent to items
        }
    }
    for (item in items) {
        if (item == function) continue
        if (!item.existsAfterExpansionSelf) continue

        val namedItem = item as? RsNamedElement ?: continue

        if (!function.namespaces.intersects(namedItem.namespaces)) continue
        if (namedItem.name == config.name) {
            val presentation = getPresentation(owner)
            val prefix = if (owner is RsImplItem) "impl " else ""
            val ownerName = "${prefix}${presentation.presentableText.orEmpty()} ${presentation.locationString.orEmpty()}"
            map.putValue(namedItem, RsBundle.message("refactoring.change.signature.name.conflict", config.name, ownerName))
        }
    }
}
