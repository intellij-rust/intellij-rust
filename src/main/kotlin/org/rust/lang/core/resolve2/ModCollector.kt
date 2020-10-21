/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubTreeLoader
import org.rust.lang.RsConstants
import org.rust.lang.RsFileType
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.macros.RangeMap
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.isEnabledByCfgSelf
import org.rust.lang.core.psi.ext.variants
import org.rust.lang.core.resolve.namespaces
import org.rust.lang.core.resolve.processModDeclResolveVariants
import org.rust.lang.core.resolve2.util.RESOLVE_RANGE_MAP_KEY
import org.rust.lang.core.stubs.*
import org.rust.openapiext.fileId
import org.rust.openapiext.findFileByMaybeRelativePath
import org.rust.openapiext.pathAsPath
import org.rust.openapiext.toPsiFile

class ModCollectorContext(
    val defMap: CrateDefMap,
    val crateRoot: ModData,
    val context: CollectorContext,
    val macroDepth: Int = 0,
    /**
     * called when new [RsItemElement] is found
     * default behaviour: just add it to [ModData.visibleItems]
     * behaviour when processing expanded items:
     * add it to [ModData.visibleItems] and propagate to modules which have glob import from [ModData]
     */
    val onAddItem: (ModData, String, PerNs) -> Unit =
        { containingMod, name, perNs -> containingMod.addVisibleItem(name, perNs) }
)

fun collectFileAndCalculateHash(file: RsFile, modData: ModData, context: ModCollectorContext) {
    val hashCalculator = HashCalculator()
    val collector = ModCollector(modData, context, hashCalculator)
    collector.collectMod(file.getOrBuildStub() ?: return)
    val fileHash = hashCalculator.getFileHash()
    context.defMap.addVisitedFile(file, modData, fileHash)
}

fun collectExpandedElements(expandedFile: RsFileStub, modData: ModData, context: ModCollectorContext) {
    val collector = ModCollector(modData, context, hashCalculator = null)
    collector.collectMod(expandedFile)
}

/**
 * Collects explicitly declared items in all modules of crate.
 * Also populates [CollectorContext.imports] and [CollectorContext.macroCalls] in [context].
 */
private class ModCollector(
    private val modData: ModData,
    private val context: ModCollectorContext,
    private val hashCalculator: HashCalculator?,
) : ModVisitor {

    private val defMap: CrateDefMap = context.defMap
    private val crateRoot: ModData = context.crateRoot
    private val macroDepth: Int = context.macroDepth
    private val onAddItem: (ModData, String, PerNs) -> Unit = context.onAddItem
    private val crate: Crate = context.context.crate
    private val project: Project = context.context.project

    fun collectMod(mod: StubElement<out RsMod>) {
        val visitor = if (hashCalculator != null) {
            val hashVisitor = hashCalculator.getVisitor(crate, modData.fileRelativePath)
            CompositeModVisitor(hashVisitor, this)
        } else {
            this
        }
        ModCollectorBase.collectMod(mod, modData.isDeeplyEnabledByCfg, visitor, crate)
        if (isUnitTestMode) {
            modData.checkChildModulesAndVisibleItemsConsistency()
        }
    }

    override fun collectImport(import: ImportLight) {
        context.context.imports += Import(
            containingMod = modData,
            usePath = import.usePath,
            nameInScope = import.nameInScope,
            visibility = convertVisibility(import.visibility, import.isDeeplyEnabledByCfg),
            isGlob = import.isGlob,
            isExternCrate = import.isExternCrate,
            isPrelude = import.isPrelude
        )

        if (import.isDeeplyEnabledByCfg && import.isExternCrate && import.isMacroUse) {
            defMap.importExternCrateMacros(import.usePath.single())
        }
    }

    override fun collectItem(item: ItemLight, stub: RsNamedStub) {
        val name = item.name

        // could be null if `.resolve()` on `RsModDeclItem` returns null
        val childModData = tryCollectChildModule(item, stub)

        val visItem = convertToVisItem(item, stub)
        if (visItem.isModOrEnum && childModData == null) return
        val perNs = PerNs(visItem, item.namespaces)
        onAddItem(modData, name, perNs)

        // we have to check `modData[name]` to be sure that `childModules` and `visibleItems` are consistent
        if (childModData != null && perNs.types === modData.getVisibleItem(name).types) {
            modData.childModules[name] = childModData
        }
    }

    private fun convertToVisItem(item: ItemLight, stub: RsNamedStub): VisItem {
        val visibility = convertVisibility(item.visibility, item.isDeeplyEnabledByCfg)
        val itemPath = modData.path.append(item.name)
        val isModOrEnum = stub is RsModItemStub || stub is RsModDeclItemStub || stub is RsEnumItemStub
        return VisItem(itemPath, visibility, isModOrEnum)
    }

    private fun tryCollectChildModule(item: ItemLight, stub: RsNamedStub): ModData? {
        if (stub is RsEnumItemStub) return collectEnumAsModData(item, stub)

        val childMod = when (stub) {
            is RsModItemStub -> ChildMod.Inline(stub, item.name)
            is RsModDeclItemStub -> {
                val childModPsi = resolveModDecl(item.name, item.pathAttribute) ?: return null
                ChildMod.File(childModPsi, item.name)
            }
            else -> return null
        }
        val isDeeplyEnabledByCfg = item.isDeeplyEnabledByCfg
        val childModData = collectChildModule(childMod, isDeeplyEnabledByCfg, item.pathAttribute)
        if (item.hasMacroUse && isDeeplyEnabledByCfg) modData.legacyMacros += childModData.legacyMacros
        return childModData
    }

    private fun collectChildModule(
        childMod: ChildMod,
        isDeeplyEnabledByCfg: Boolean,
        pathAttribute: String?
    ): ModData {
        ProgressManager.checkCanceled()
        val childModPath = modData.path.append(childMod.name)
        val (fileId, fileRelativePath) = when (childMod) {
            is ChildMod.File -> childMod.file.virtualFile.fileId to ""
            is ChildMod.Inline -> modData.fileId to "${modData.fileRelativePath}::${childMod.name}"
        }
        val childModData = ModData(
            parent = modData,
            crate = modData.crate,
            path = childModPath,
            isDeeplyEnabledByCfg = isDeeplyEnabledByCfg,
            fileId = fileId,
            fileRelativePath = fileRelativePath,
            ownedDirectoryId = childMod.getOwnedDirectory(modData, pathAttribute)?.fileId,
            crateDescription = defMap.crateDescription
        )
        childModData.legacyMacros += modData.legacyMacros

        when (childMod) {
            is ChildMod.Inline -> {
                val collector = ModCollector(childModData, context, hashCalculator)
                collector.collectMod(childMod.mod)
            }
            is ChildMod.File -> collectFileAndCalculateHash(childMod.file, childModData, context)
        }
        return childModData
    }

    private fun collectEnumAsModData(enum: ItemLight, enumStub: RsEnumItemStub): ModData {
        val enumName = enum.name
        val enumPath = modData.path.append(enumName)
        val enumData = ModData(
            parent = modData,
            crate = modData.crate,
            path = enumPath,
            isDeeplyEnabledByCfg = enum.isDeeplyEnabledByCfg,
            fileId = modData.fileId,
            fileRelativePath = "${modData.fileRelativePath}::$enumName",
            ownedDirectoryId = modData.ownedDirectoryId,  // actually can use any value here
            isEnum = true,
            crateDescription = defMap.crateDescription
        )
        for (variantPsi in enumStub.variants) {
            val variantName = variantPsi.name ?: continue
            val variantPath = enumPath.append(variantName)
            val isVariantDeeplyEnabledByCfg = enumData.isDeeplyEnabledByCfg && variantPsi.isEnabledByCfgSelf(crate)
            val variantVisibility = if (isVariantDeeplyEnabledByCfg) Visibility.Public else Visibility.CfgDisabled
            val variant = VisItem(variantPath, variantVisibility)
            val variantPerNs = PerNs(variant, variantPsi.namespaces)
            enumData.addVisibleItem(variantName, variantPerNs)
        }
        return enumData
    }

    override fun collectMacroCall(call: MacroCallLight, stub: RsMacroCallStub) {
        check(modData.isDeeplyEnabledByCfg) { "for performance reasons cfg-disabled macros should not be collected" }
        val bodyHash = call.bodyHash
        if (bodyHash == null && call.path.last() != "include") return
        val macroDef = call.path.singleOrNull()?.let { modData.legacyMacros[it] }
        val dollarCrateMap = stub.getUserData(RESOLVE_RANGE_MAP_KEY) ?: RangeMap.EMPTY
        context.context.macroCalls += MacroCallInfo(modData, call.path, call.body, bodyHash, macroDepth, macroDef, dollarCrateMap)
    }

    override fun collectMacroDef(def: MacroDefLight) {
        val bodyHash = def.bodyHash ?: return
        val macroPath = modData.path.append(def.name)

        val defInfo = MacroDefInfo(
            modData.crate,
            macroPath,
            def.body,
            bodyHash,
            def.hasMacroExport,
            def.hasLocalInnerMacros,
            project
        )
        modData.legacyMacros[def.name] = defInfo

        if (def.hasMacroExport) {
            val visItem = VisItem(macroPath, Visibility.Public)
            val perNs = PerNs(macros = visItem)
            onAddItem(crateRoot, def.name, perNs)
        }
    }

    private fun convertVisibility(visibility: VisibilityLight, isDeeplyEnabledByCfg: Boolean): Visibility {
        if (!isDeeplyEnabledByCfg) return Visibility.CfgDisabled
        return when (visibility) {
            VisibilityLight.Public -> Visibility.Public
            // TODO: Optimization - create Visibility.Crate and Visibility.Private to reduce allocations
            VisibilityLight.RestrictedCrate -> Visibility.Restricted(crateRoot)
            VisibilityLight.Private -> Visibility.Restricted(modData)
            is VisibilityLight.Restricted -> resolveRestrictedVisibility(visibility.inPath, crateRoot, modData)
        }
    }

    /** See also [processModDeclResolveVariants] */
    private fun resolveModDecl(name: String, pathAttribute: String?): RsFile? {
        val (parentDirectory, fileNames) = if (pathAttribute == null) {
            val parentDirectory = modData.getOwnedDirectory() ?: return null
            val fileNames = arrayOf("$name.rs", "$name/mod.rs")
            parentDirectory to fileNames
        } else {
            // https://doc.rust-lang.org/reference/items/modules.html#the-path-attribute
            val parentDirectory = if (modData.isRsFile) {
                // For path attributes on modules not inside inline module blocks,
                // the file path is relative to the directory the source file is located.
                val containingFile = modData.asVirtualFile() ?: return null
                containingFile.parent
            } else {
                // Paths for path attributes inside inline module blocks are relative to
                // the directory of file including the inline module components as directories.
                modData.getOwnedDirectory()
            } ?: return null
            val explicitPath = FileUtil.toSystemIndependentName(pathAttribute)
            parentDirectory to arrayOf(explicitPath)
        }

        val virtualFiles = fileNames.mapNotNull { parentDirectory.findFileByMaybeRelativePath(it) }
        // Note: It is possible that [virtualFiles] is not empty,
        // but result is null, when e.g. file is too big (thus will be [PsiFile] and not [RsFile])
        if (virtualFiles.isEmpty()) {
            for (fileName in fileNames) {
                val path = parentDirectory.pathAsPath.resolve(fileName)
                defMap.missedFiles.add(path)
            }
        }
        return virtualFiles.singleOrNull()?.toPsiFile(project) as? RsFile
    }
}

fun RsFile.getOrBuildStub(): RsFileStub? {
    val stubTree = greenStubTree ?: StubTreeLoader.getInstance().readOrBuild(project, virtualFile, this)
    val stub = stubTree?.root as? RsFileStub
    if (stub == null) RESOLVE_LOG.error("No stub for file ${virtualFile.path}")
    return stub
}

// `#[macro_use] extern crate <name>;` - import macros
fun CrateDefMap.importExternCrateMacros(externCrateName: String) {
    val externCrateDefMap = resolveExternCrateAsDefMap(externCrateName) ?: return
    importAllMacrosExported(externCrateDefMap)
}

// https://doc.rust-lang.org/reference/visibility-and-privacy.html#pubin-path-pubcrate-pubsuper-and-pubself
private fun resolveRestrictedVisibility(
    path: Array<String>,
    crateRoot: ModData,
    containingMod: ModData
): Visibility.Restricted {
    val initialModData = when (path.first()) {
        "super", "self" -> containingMod
        else -> crateRoot
    }
    val pathTarget = path
        .fold(initialModData) { modData, segment ->
            val nextModData = when (segment) {
                "self" -> modData
                "super" -> modData.parent
                else -> modData.childModules[segment]
            }
            nextModData ?: return Visibility.Restricted(crateRoot)
        }
    return Visibility.Restricted(pathTarget)
}

private fun ModData.checkChildModulesAndVisibleItemsConsistency() {
    for ((name, childMod) in childModules) {
        check(name == childMod.name) { "Inconsistent name of $childMod" }
        check(visibleItems[name]?.types?.isModOrEnum == true)
        { "Inconsistent `visibleItems` and `childModules` in $this for name $name" }
    }
}

private fun ModData.getOwnedDirectory(): VirtualFile? {
    val ownedDirectoryId = ownedDirectoryId ?: return null
    return PersistentFS.getInstance().findFileById(ownedDirectoryId)
}

private fun ModData.asVirtualFile(): VirtualFile? {
    check(isRsFile)
    return PersistentFS.getInstance().findFileById(fileId)
        ?: run {
            RESOLVE_LOG.error("Can't find VirtualFile for $this")
            return null
        }
}

private sealed class ChildMod(val name: String) {
    class Inline(val mod: RsModItemStub, name: String) : ChildMod(name)
    class File(val file: RsFile, name: String) : ChildMod(name)
}

/**
 * Have to pass [pathAttribute], because [RsFile.pathAttribute] triggers resolve.
 * See also: [RsMod.getOwnedDirectory]
 */
private fun ChildMod.getOwnedDirectory(parentMod: ModData, pathAttribute: String?): VirtualFile? {
    if (this is ChildMod.File && name == RsConstants.MOD_RS_FILE) return file.virtualFile.parent

    val (parentDirectory, path) = if (pathAttribute != null) {
        when {
            this is ChildMod.File -> return file.virtualFile.parent
            parentMod.isRsFile -> parentMod.asVirtualFile()?.parent to pathAttribute
            else -> parentMod.getOwnedDirectory() to pathAttribute
        }
    } else {
        parentMod.getOwnedDirectory() to name
    }
    if (parentDirectory == null) return null

    // Don't use `FileUtil#getNameWithoutExtension` to correctly process relative paths like `./foo`
    val directoryPath = FileUtil.toSystemIndependentName(path).removeSuffix(".${RsFileType.defaultExtension}")
    return parentDirectory.findFileByMaybeRelativePath(directoryPath)
}
