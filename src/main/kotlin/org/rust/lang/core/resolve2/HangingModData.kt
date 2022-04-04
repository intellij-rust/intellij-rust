/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER
import org.rust.lang.core.macros.decl.MACRO_DOLLAR_CRATE_IDENTIFIER_REGEX
import org.rust.lang.core.macros.findMacroCallExpandedFromNonRecursive
import org.rust.lang.core.macros.ranges
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.RsModInfoBase.InfoNotFound
import org.rust.lang.core.resolve2.RsModInfoBase.RsModInfo
import org.rust.lang.core.resolve2.util.DollarCrateHelper
import org.rust.openapiext.getCachedOrCompute
import java.lang.ref.SoftReference

/**
 * Consider code:
 * ```kotlin
 * /// mod scope
 * fn main() {  /// local scope
 *     use mod1::mod2;
 *     if true {  /// nested local scope
 *         use mod2::func;
 *         func();
 *     }
 * }
 * ```
 * Here we have three scopes, module scope and two local scopes.
 * Module scope will have usual [ModData] which are computed beforehand and stored in [DefMapService].
 * Local scope will have hanging [ModData] which is computed on-demand.
 * It will live in "context" of containing mod [ModData].
 * New resolve in local scopes handles local imports and local items, local bindings are handled using old resolve.
 *
 * [scope] is [RsBlock] or [RsCodeFragment].
 */
fun getHangingModInfo(scope: RsItemsOwner): RsModInfoBase {
    if (!shouldCreateHangingModInfo(scope)) return InfoNotFound

    val contextInfo = scope.getContextModInfo()
    if (contextInfo !is RsModInfo) return contextInfo

    return getHangingModInfo(scope, contextInfo)
}

private fun getHangingModInfo(scope: RsItemsOwner, contextInfo: RsModInfo): RsModInfo {
    val (project, defMap, contextData) = contextInfo
    val modificationStamp = scope.stubAncestorStrict<RsFunction>()?.modificationTracker?.modificationCount
        ?: scope.containingFile.modificationStamp
    val dependencies = listOf(defMap.timestamp, contextData.timestamp, modificationStamp)
    val hangingModData = getCachedOrCompute(scope, HANGING_MOD_DATA_KEY, dependencies) {
        createHangingModData(scope, contextInfo)
    }
    val dataPsiHelper = LocalScopeDataPsiHelper(scope, hangingModData, contextInfo.dataPsiHelper)
    return RsModInfo(project, defMap, hangingModData, contextInfo.crate, dataPsiHelper)
}

fun getLocalModInfo(scope: RsMod): RsModInfoBase {
    val context = scope.context ?: return InfoNotFound
    if (context !is RsBlock) return RsModInfoBase.CantUseNewResolve("local nested mod")
    val contextInfo = getHangingModInfo(context)
    if (contextInfo !is RsModInfo) return contextInfo
    val (project, defMap, _, crate, _) = contextInfo

    val modData = contextInfo.modData.childModules[scope.name] ?: return InfoNotFound
    val dataPsiHelper = LocalScopeDataPsiHelper(scope, modData, contextInfo.dataPsiHelper)
    return RsModInfo(project, defMap, modData, crate, dataPsiHelper)
}

fun getNearestAncestorModInfo(scope: RsItemsOwner): RsModInfoBase {
    if (scope !is RsBlock) return getModInfo(scope)

    return getHangingModInfo(scope) as? RsModInfo
        ?: scope.getContextModInfo()
}

private fun createHangingModData(scope: RsItemsOwner, contextInfo: RsModInfo): ModData {
    val (project, defMap, contextData, crate) = contextInfo

    val pathSegment = if (scope is RsModItem) "local#${scope.name}" else "#block"
    val hangingModData = ModData(
        parent = contextData.parent,
        crate = contextData.crate,
        path = contextData.path.append(pathSegment),
        /** Affects [resolveMacroCallToLegacyMacroDefInfo] */
        macroIndex = contextData.macroIndex.append(Int.MAX_VALUE),
        isDeeplyEnabledByCfgOuter = contextData.isDeeplyEnabledByCfgOuter,
        isEnabledByCfgInner = scope.isEnabledByCfg(crate),
        fileId = null,
        fileRelativePath = "",
        ownedDirectoryId = null,
        hasPathAttribute = false,
        hasMacroUse = false,
        isNormalCrate = false,
        context = contextData,
        crateDescription = "$pathSegment in ${contextData.crateDescription}",
    )

    val collectorContext = CollectorContext(crate, project, hangingModData)
    val modCollectorContext = ModCollectorContext(defMap, collectorContext)
    val dollarCrateHelper = createDollarCrateHelper(scope)
    collectScope(scope, hangingModData, modCollectorContext, dollarCrateHelper = dollarCrateHelper)

    val indicator = ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
    DefCollector(project, defMap, collectorContext, pool = null, indicator).collect()
    return hangingModData
}

private val HANGING_MOD_DATA_KEY: Key<SoftReference<Pair<ModData, List<Long>>>> = Key.create("HANGING_MOD_DATA_KEY")

private fun RsElement.getContextModInfo(): RsModInfoBase {
    val context = contextStrict<RsItemsOwner>() ?: return InfoNotFound
    return when {
        context is RsMod -> getModInfo(context)
        context is RsBlock && shouldCreateHangingModInfo(context) -> getHangingModInfo(context)
        else -> context.getContextModInfo()
    }
}

private fun shouldCreateHangingModInfo(scope: RsItemsOwner): Boolean =
    scope.itemsAndMacros.any { it is RsItemElement || it is RsMacro || it is RsMacroCall }

private fun createDollarCrateHelper(scope: RsItemsOwner): DollarCrateHelper? {
    val call = scope.findMacroCallExpandedFromNonRecursive() as? RsMacroCall ?: return null
    val expansion = call.expansion ?: return null
    val expansionText = expansion.file.text
    if (!expansionText.contains(MACRO_DOLLAR_CRATE_IDENTIFIER)) return null

    val defCrate = call.resolveToMacroAndGetContainingCrate()?.id ?: return null
    val rangesInExpansion = MACRO_DOLLAR_CRATE_IDENTIFIER_REGEX.findAll(expansionText)
        .associate { it.range.first to defCrate }
    // TODO: Proper implementation with support of `defHasLocalInnerMacros`, macro expanded to macro, etc
    return DollarCrateHelper(expansion.ranges, rangesInExpansion, defHasLocalInnerMacros = false, defCrate)
}

private class LocalScopeDataPsiHelper(
    private val scope: RsItemsOwner,
    private val modData: ModData,
    private val delegate: DataPsiHelper?,
) : DataPsiHelper {
    override fun psiToData(scope: RsItemsOwner): ModData? {
        if (scope == this.scope) return modData
        return delegate?.psiToData(scope)
    }

    override fun dataToPsi(data: ModData): RsItemsOwner? {
        if (data == modData) return scope
        data.path.getRelativePathTo(modData.path)?.let { relativePath ->
            return relativePath.fold(scope as RsItemsOwner?) { scope, segment ->
                scope?.getChildModule(segment)
            }
        }
        return delegate?.dataToPsi(data)
    }

    override fun findModData(path: ModPath): ModData? =
        findHangingModData(path, modData) ?: delegate?.findModData(path)
}

fun findHangingModData(path: ModPath, hangingModData: ModData): ModData? {
    if (path == hangingModData.path) return hangingModData
    path.getRelativePathTo(hangingModData.path)?.let { relativePath ->
        return hangingModData.getChildModData(relativePath)
    }
    return null
}

/** 'mod1::mod2::#block::local1::local2'.getRelativePathTo('mod1::mod2::#block') == 'local1::local2' */
private fun ModPath.getRelativePathTo(parent: ModPath): Array<String>? =
    if (parent.isSubPathOf(this)) {
        segments.copyOfRange(parent.segments.size, segments.size)
    } else {
        null
    }
