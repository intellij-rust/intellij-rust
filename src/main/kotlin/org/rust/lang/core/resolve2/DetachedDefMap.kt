/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.impl.FakeDetachedCrate
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.ext.RsItemsOwner
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.getChildModule
import org.rust.openapiext.getCachedOrCompute
import java.lang.ref.SoftReference

fun Project.getDetachedModInfo(scope: RsMod): RsModInfoBase {
    val rootMod = scope.containingFile as? RsFile ?: return RsModInfoBase.InfoNotFound
    val crate = FakeDetachedCrate(rootMod, DefMapService.getDetachedCrateNextId(), dependencies = emptyList())
    return getModInfoInDetachedCrate(scope, crate)
}

private fun Project.getModInfoInDetachedCrate(scope: RsMod, crate: Crate): RsModInfoBase {
    val defMap = defMapService.getDetachedDefMap(crate) ?: return RsModInfoBase.InfoNotFound
    val dataPsiHelper = DetachedFileDataPsiHelper(crate.rootMod ?: return RsModInfoBase.InfoNotFound, defMap)
    val modData = dataPsiHelper.psiToData(scope) ?: return RsModInfoBase.InfoNotFound
    return RsModInfoBase.RsModInfo(this, defMap, modData, crate, dataPsiHelper)
}

private class DetachedFileDataPsiHelper(
    private val root: RsFile,
    private val defMap: CrateDefMap
) : DataPsiHelper {
    override fun psiToData(scope: RsItemsOwner): ModData? {
        return when {
            scope.containingFile != root -> null
            scope == root -> defMap.root
            scope is RsModItem -> {
                val superMod = scope.`super` ?: return null
                val superModData = psiToData(superMod) ?: return null
                superModData.childModules[scope.modName]
            }
            else -> null
        }
    }

    override fun dataToPsi(data: ModData): RsMod? {
        return when {
            data.crate != defMap.crate -> null
            data == defMap.root -> root
            else -> {
                val superModData = data.parent ?: return null
                val superMod = dataToPsi(superModData) ?: return null
                superMod.getChildModule(data.name)
            }
        }
    }
}

private fun DefMapService.getDetachedDefMap(crate: Crate): CrateDefMap? {
    check(crate.id != null)
    val crateRoot = crate.rootMod ?: return null

    val allDependenciesDefMaps = crate.getAllDependenciesDefMaps()

    val dependenciesStamps = allDependenciesDefMaps.map {
        val holder = getDefMapHolder(it.value.crate)
        holder.modificationCount
    }
    val dependencies = dependenciesStamps + crateRoot.modificationStamp

    return getCachedOrCompute(crateRoot, DEF_MAP_KEY, dependencies) {
        val indicator = ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
        buildDefMap(crate, allDependenciesDefMaps, pool = null, indicator, isNormalCrate = false)
            ?: error("null detached DefMap")
    }
}

private val DEF_MAP_KEY: Key<SoftReference<Pair<CrateDefMap, List<Long>>>> = Key.create("DEF_MAP_KEY")

private fun Crate.getAllDependenciesDefMaps(): Map<Crate, CrateDefMap> {
    val allDependencies = flatDependencies
    val ids = allDependencies.mapNotNull { it.id }
    val crateById = allDependencies.associateBy { it.id }
    val defMapById = project.defMapService.getOrUpdateIfNeeded(ids)
    return defMapById.mapNotNull { (crateId, defMap) ->
        val crate = crateById[crateId]
        if (crate != null && defMap != null) {
            crate to defMap
        } else {
            null
        }
    }.toMap(hashMapOf())
}
