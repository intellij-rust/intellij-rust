/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.impl.CargoBasedCrate
import org.rust.lang.core.crate.impl.DoctestCrate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ItemProcessingMode.WITHOUT_PRIVATE_IMPORTS
import org.rust.lang.core.resolve2.RsModInfoBase.*
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.toPsiFile

val isNewResolveEnabled: Boolean
    get() = isFeatureEnabled(RsExperiments.RESOLVE_NEW_ENGINE)

/** null return value means that new resolve can't be used */
fun processItemDeclarations2(
    scope: RsMod,
    ns: Set<Namespace>,
    processor: RsResolveProcessor,
    ipm: ItemProcessingMode
): Boolean? {
    val (project, defMap, modData) = when (val info = getModInfo(scope)) {
        is CantUseNewResolve -> return null
        InfoNotFound -> return false
        is RsModInfo -> info
    }

    for ((name, perNs) in modData.visibleItems.entriesWithName(processor.name)) {
        val visItems = arrayOf(
            perNs.types to Namespace.Types,
            perNs.values to Namespace.Values,
            perNs.macros to Namespace.Macros,
        )
        // We need a `Set` here because item could belong to multiple namespaces (e.g. unit struct)
        // Also we need to distinguish unit struct and e.g. mod and function with same name in one module
        val elements = hashSetOf<RsNamedElement>()
        for ((visItem, namespace) in visItems) {
            if (visItem == null || namespace !in ns) continue
            if (ipm == WITHOUT_PRIVATE_IMPORTS && visItem.visibility == Visibility.Invisible) continue
            elements += visItem.toPsi(defMap, project, namespace)
        }
        elements.any { processor(name, it) } && return true
    }

    if (processor.name == null && Namespace.Types in ns) {
        for ((traitPath, traitVisibility) in modData.unnamedTraitImports) {
            val trait = VisItem(traitPath, traitVisibility)
            val traitPsi = trait.toPsi(defMap, project, Namespace.Types)
            traitPsi.any { processor("_", it) } && return true
        }
    }

    if (ipm.withExternCrates && Namespace.Types in ns) {
        for ((name, externCrateDefMap) in defMap.externPrelude.entriesWithName(processor.name)) {
            if (modData.visibleItems[name]?.types != null) continue
            val externCrateRoot = externCrateDefMap.root.toRsMod(project) ?: continue
            processor(name, externCrateRoot) && return true
        }
    }

    return false
}

private sealed class RsModInfoBase {
    /** [reason] is only for debug */
    class CantUseNewResolve(val reason: String) : RsModInfoBase()
    object InfoNotFound : RsModInfoBase()
    data class RsModInfo(val project: Project, val defMap: CrateDefMap, val modData: ModData) : RsModInfoBase()
}

private fun getModInfo(scope: RsMod): RsModInfoBase {
    val project = scope.project
    if (!isNewResolveEnabled) return CantUseNewResolve("not enabled")
    if (scope.modName == TMP_MOD_NAME) return CantUseNewResolve("__tmp__ mod")
    if (scope.isLocal) return CantUseNewResolve("local mod")
    val crate = scope.containingCrate as? CargoBasedCrate ?: return CantUseNewResolve("not CargoBasedCrate")

    val defMap = project.getDefMap(crate) ?: return InfoNotFound
    val modData = defMap.getModData(scope) ?: return InfoNotFound

    if (isModShadowedByOtherMod(scope, modData)) return CantUseNewResolve("mod shadowed by other mod")

    return RsModInfo(project, defMap, modData)
}

private fun Project.getDefMap(crate: Crate): CrateDefMap? {
    check(crate !is DoctestCrate) { "doc test crates are not supported by CrateDefMap" }
    val crateId = crate.id ?: return null
    val defMap = defMapService.getOrUpdateIfNeeded(crateId)
    if (defMap == null) RESOLVE_LOG.error("DefMap is null for $crate during resolve")
    return defMap
}

/** E.g. `fn func() { mod foo { ... } }` */
private val RsMod.isLocal: Boolean
    get() = ancestorStrict<RsBlock>() != null

/** "shadowed by other mod" means that [ModData] is not accessible from [CrateDefMap.root] through [ModData.childModules] */
private fun isModShadowedByOtherMod(mod: RsMod, modData: ModData): Boolean {
    val isDeeplyEnabledByCfg = (mod.containingFile as RsFile).isDeeplyEnabledByCfg && mod.isEnabledByCfg
    val isShadowedByOtherInlineMod = isDeeplyEnabledByCfg != modData.isDeeplyEnabledByCfg

    return modData.isShadowedByOtherFile || isShadowedByOtherInlineMod
}

private fun <T> Map<String, T>.entriesWithName(name: String?): Map<String, T> {
    if (name == null) {
        return this
    } else {
        val value = this[name] ?: return emptyMap()
        return mapOf(name to value)
    }
}

private fun VisItem.toPsi(defMap: CrateDefMap, project: Project, ns: Namespace): List<RsNamedElement> {
    if (isModOrEnum) return path.toRsModOrEnum(defMap, project)
    val containingModOrEnum = containingMod.toRsModOrEnum(defMap, project).singleOrNull() ?: return emptyList()
    return when (containingModOrEnum) {
        is RsMod -> {
            if (ns == Namespace.Macros) {
                // TODO: expandedItemsIncludingMacros
                val macros = containingModOrEnum.itemsAndMacros
                    .filterIsInstance<RsNamedElement>()
                    .filter {
                        (it is RsMacro || it is RsMacro2) && it.name == name && matchesIsEnabledByCfg(it, this)
                    }
                // TODO: Multiresolve for macro 2.0
                val macro = macros.lastOrNull()
                listOfNotNull(macro)
            } else {
                containingModOrEnum
                    .getExpandedItemsWithName<RsNamedElement>(name)
                    .filter { ns in it.namespaces && matchesIsEnabledByCfg(it, this) }
            }
        }
        is RsEnumItem -> {
            containingModOrEnum.variants
                .filter { it.name == name && ns in it.namespaces && matchesIsEnabledByCfg(it, this) }
        }
        else -> error("Expected mod or enum, got: $containingModOrEnum")
    }
}

private fun matchesIsEnabledByCfg(itemPsi: RsNamedElement, item: VisItem): Boolean =
    if (item.isEnabledByCfg) {
        itemPsi.isEnabledByCfg
    } else {
        // If inside cfg-disabled module we have import to cfg-enabled item,
        // then this item will be imported with CfgDisabled visibility
        true
    }

private val VisItem.isEnabledByCfg: Boolean get() = visibility != Visibility.CfgDisabled

private fun ModPath.toRsModOrEnum(defMap: CrateDefMap, project: Project): List<RsNamedElement /* RsMod or RsEnumItem */> {
    val modData = defMap.getModData(this) ?: return emptyList()
    return if (modData.isEnum) {
        modData.toRsEnum(project)
    } else {
        val mod = modData.toRsMod(project)
        listOfNotNull(mod)
    }
}

private fun ModData.toRsEnum(project: Project): List<RsEnumItem> {
    if (!isEnum) return emptyList()
    val containingMod = parent?.toRsMod(project) ?: return emptyList()
    val visItem = asVisItem()
    return containingMod
        .getExpandedItemsWithName<RsEnumItem>(name)
        .filter { matchesIsEnabledByCfg(it, visItem) }
}

private fun ModData.toRsMod(project: Project): RsMod? = toRsModNullable(project)
    ?: run {
        RESOLVE_LOG.warn("Can't find RsMod for $this")
        null
    }

private fun ModData.toRsModNullable(project: Project): RsMod? {
    if (isEnum) return null
    val file = PersistentFS.getInstance().findFileById(fileId)
        ?.toPsiFile(project) as? RsFile
        ?: return null
    val fileRelativeSegments = fileRelativePath.split("::")
    return fileRelativeSegments
        .subList(1, fileRelativeSegments.size)
        .fold(file as RsMod) { mod, segment ->
            mod
                .getExpandedItemsWithName<RsModItem>(segment)
                .singleOrCfgEnabled()
                ?: return null
        }
}

// TODO: Multiresolve
private inline fun <reified T : RsElement> List<T>.singleOrCfgEnabled(): T? =
    singleOrNull() ?: singleOrNull { it.isEnabledByCfg }

private inline fun <reified T : RsNamedElement> RsItemsOwner.getExpandedItemsWithName(name: String): List<T> =
    getExpandedItemsWithName(name, T::class.java)

/** [expandedItemsExceptImplsAndUses] with addition of items from [RsForeignModItem]s */
private fun <T : RsNamedElement> RsItemsOwner.getExpandedItemsWithName(
    name: String,
    psiClass: Class<T>
): List<T> {
    val result = arrayListOf<T>()
    for (item in expandedItemsExceptImplsAndUses) {
        if (item is RsForeignModItem) {
            for (itemInner in PsiTreeUtil.getStubChildrenOfTypeAsList(item, psiClass)) {
                if (itemInner.name == name) {
                    result.add(itemInner)
                }
            }
        } else {
            if (item.name == name && psiClass.isInstance(item)) {
                @Suppress("UNCHECKED_CAST")
                result.add(item as T)
            }
        }
    }
    return result
}
