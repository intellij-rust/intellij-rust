/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.util.SmartList
import gnu.trove.THashMap
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.Visibility.*
import org.rust.lang.core.resolve2.util.PerNsHashMap
import org.rust.openapiext.fileId
import org.rust.openapiext.testAssert
import org.rust.stdext.HashCode
import java.nio.file.Path
import java.util.*

class CrateDefMap(
    val crate: CratePersistentId,
    val root: ModData,

    // TODO: Not used after [CrateDefMap] was built, probably it is worth to move it to [CollectorContext]
    /** Used only by `extern crate crate_name;` declarations */
    val directDependenciesDefMaps: Map<String, CrateDefMap>,
    private val allDependenciesDefMaps: Map<CratePersistentId, CrateDefMap>,
    /**
     * The prelude module for this crate. This either comes from an import
     * marked with the `prelude_import` attribute, or (in the normal case) from
     * a dependency (`std` or `core`).
     */
    var prelude: ModData?,
    val metaData: CrateMetaData,
    /** Equal to `root.macroIndex.single()` */
    val rootModMacroIndex: Int,
    /** Only for debug */
    val crateDescription: String,
) {
    /** It is needed at least to handle `extern crate name as alias;` */
    val externPrelude: MutableMap<String, CrateDefMap> = directDependenciesDefMaps.toMap(hashMapOf())

    /**
     * File included via `include!` macro has same [FileInfo.modData] as main file,
     * but different [FileInfo.hash] and [FileInfo.modificationStamp]
     */
    val fileInfos: MutableMap<FileId, FileInfo> = hashMapOf()

    /**
     * Files which currently do not exist, but could affect resolve if created:
     * - for unresolved mod declarations - `.../name.rs` and `.../name/mod.rs`
     * - for unresolved `include!` macro - corresponding file
     *
     * Note: [missedFiles] should be empty if compilation is successful.
     */
    val missedFiles: MutableList<Path> = mutableListOf()

    @VisibleForTesting
    val timestamp: Long = System.nanoTime()

    /** Stored as memory optimization */
    val rootAsPerNs: PerNs = PerNs(types = VisItem(root.path, Public, true))

    fun getDefMap(crate: CratePersistentId): CrateDefMap? =
        if (crate == this.crate) this else allDependenciesDefMaps[crate]

    fun getModData(path: ModPath): ModData? {
        val defMap = getDefMap(path.crate) ?: error("Can't find ModData for path $path")
        return defMap.doGetModData(path)
    }

    private fun doGetModData(path: ModPath): ModData? {
        check(crate == path.crate)
        return path.segments
            .fold(root as ModData?) { modData, segment -> modData?.childModules?.get(segment) }
            .also { testAssert({ it != null }, { "Can't find ModData for $path in crate $crateDescription" }) }
    }

    // TODO: Possible optimization - store in [CrateDefMap] map from [String] (mod path) to [ModData]
    fun tryCastToModData(types: VisItem): ModData? {
        if (!types.isModOrEnum) return null
        return getModData(types.path)
    }

    fun getModData(mod: RsMod): ModData? {
        if (mod is RsFile) {
            val virtualFile = mod.originalFile.virtualFile ?: return null
            val fileInfo = fileInfos[virtualFile.fileId]
            // TODO: Exception here does not fail [RsBuildDefMapTest]
            // Note: we don't expand cfg-disabled macros (it can contain mod declaration)
            testAssert({ fileInfo != null || !mod.isDeeplyEnabledByCfg }, { "todo" })
            return fileInfo?.modData
        }
        val parentMod = mod.`super` ?: return null
        val parentModData = getModData(parentMod) ?: return null
        return parentModData.childModules[mod.modName]
    }

    fun getMacroInfo(macroDef: VisItem): MacroDefInfo? {
        val defMap = getDefMap(macroDef.crate) ?: error("Can't find DefMap for macro $macroDef")
        return defMap.doGetMacroInfo(macroDef)
    }

    // TODO: [RsMacro2]
    private fun doGetMacroInfo(macroDef: VisItem): MacroDefInfo? {
        val containingMod = getModData(macroDef.containingMod) ?: error("Can't find ModData for macro $macroDef")
        if (macroDef.name in containingMod.procMacros) return null
        val macroInfos = containingMod.legacyMacros[macroDef.name]
            ?: error("Can't find definition for macro $macroDef")
        return macroInfos.singlePublicOrFirst()
    }

    /**
     * Import all exported macros from another crate.
     *
     * Exported macros are just all macros in the root module scope.
     * Note that it contains not only all ```#[macro_export]``` macros, but also all aliases
     * created by `use` in the root module, ignoring the visibility of `use`.
     */
    fun importAllMacrosExported(from: CrateDefMap) {
        for ((name, def) in from.root.visibleItems) {
            // `macro_use` only bring things into legacy scope.
            val macroDef = def.macros ?: continue
            val macroInfo = from.getMacroInfo(macroDef) ?: continue
            root.addLegacyMacro(name, macroInfo)
        }
    }

    fun addVisitedFile(file: RsFile, modData: ModData, fileHash: HashCode) {
        val fileId = file.virtualFile.fileId
        // TODO: File included in module tree multiple times ?
        // testAssert { fileId !in fileInfos }
        fileInfos[fileId] = FileInfo(file.modificationStampForResolve, modData, fileHash)
    }

    override fun toString(): String = crateDescription
}

/** Refers to [VirtualFileWithId.getId] */
typealias FileId = Int

class FileInfo(
    /**
     * Result of [FileViewProvider.getModificationStamp].
     *
     * Here are possible (other) methods to use:
     * - [PsiFile.getModificationStamp]
     * - [FileViewProvider.getModificationStamp]
     * - [VirtualFile.getModificationStamp]
     * - [VirtualFile.getModificationCount]
     * - [Document.getModificationStamp]
     *
     * Notes:
     * - [VirtualFile] methods value is updated only after file is saved to disk
     * - Only [VirtualFile.getModificationCount] survives IDE restart
     */
    val modificationStamp: Long,
    /** Optimization for [CrateDefMap.getModData] */
    val modData: ModData,
    val hash: HashCode,
)

/**
 * We give macro index to each macro def, macro call, and mod.
 *
 * Consider macro call `foo1` inside `mod1::mod2`.
 * Then `foo1` will have macro index `[crateIndex, index1, index2, localIndex1]`, where
 * - `crateIndex` - unique index of crate, greater then all indexes of all crate dependencies
 * - `index1` - local index of `mod1` (in crate root)
 * - `index2` - local index of `mod2` (in `mod1`)
 * - `localIndex1` - local index of macro call (in `mod2`)
 *
 * Consider `foo1` macro call expands to `foo2` macro call, which expands to `foo3` macro call.
 * Then `foo2` will have macro index `[crateIndex, index1, index2, localIndex1, localIndex2, localIndex3]`, where
 * - `localIndex2` - local index of `foo2` inside elements expanded from `foo1`
 *
 * MacroIndex is used:
 * - when resolving macro call, to choose macro def lexically before macro call
 * - when propagating macros from mod with `macro_use` attribute,
 *   to determine modules to which propagate
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class MacroIndex(private val indices: IntArray) : Comparable<MacroIndex> {
    fun append(index: Int): MacroIndex = MacroIndex(indices + index)

    override fun compareTo(other: MacroIndex): Int = Arrays.compare(indices, other.indices)

    companion object {
        /** Equivalent to `call < mod && !isPrefix(call, mod)` */
        fun shouldPropagate(call: MacroIndex, mod: MacroIndex): Boolean {
            val callIndices = call.indices
            val modIndices = mod.indices
            val commonPrefix = Arrays.mismatch(callIndices, modIndices)
            return commonPrefix != callIndices.size
                && commonPrefix != modIndices.size
                && callIndices[commonPrefix] < modIndices[commonPrefix]
        }

        fun equals(index1: MacroIndex, index2: MacroIndex): Boolean = index1.indices.contentEquals(index2.indices)
    }

    override fun toString(): String = indices.contentToString()
}

class ModData(
    val parent: ModData?,
    val crate: CratePersistentId,
    val path: ModPath,
    val macroIndex: MacroIndex,
    /**
     * For [RsMod]:
     * - [isEnabledByCfgInner] is always true
     * - [isDeeplyEnabledByCfgOuter] equals to [isDeeplyEnabledByCfg]
     * For [RsFile]:
     * - [isEnabledByCfgInner] corresponds to file level cfg attributes - `#![cfg(...)]`
     * - [isDeeplyEnabledByCfgOuter] corresponds to `parent.isDeeplyEnabledByCfg` plus cfg attributes on mod declaration
     */
    val isDeeplyEnabledByCfgOuter: Boolean,
    val isEnabledByCfgInner: Boolean,
    /** id of containing file */
    val fileId: FileId,
    // TODO: Possible optimization - store as Array<String>
    /** Starts with :: */
    val fileRelativePath: String,
    /** `fileId` of owning directory */
    val ownedDirectoryId: FileId?,
    val hasMacroUse: Boolean,
    val isEnum: Boolean = false,
    /** Only for debug */
    val crateDescription: String,
) {
    /** `true` if the module is a separate `.rs` file (not an inline module) */
    val isRsFile: Boolean get() = fileRelativePath.isEmpty()
    val isCrateRoot: Boolean get() = parent == null
    val name: String get() = path.name
    val isDeeplyEnabledByCfg: Boolean get() = isDeeplyEnabledByCfgOuter && isEnabledByCfgInner
    val parents: Sequence<ModData> get() = generateSequence(this) { it.parent }
    private val rootModData: ModData = parent?.rootModData ?: this

    // Optimization to reduce allocations
    val visibilityInSelf: Restricted = Restricted.create(this)

    // Type is basically `MutableMap<String, PerNs>`
    val visibleItems: PerNsHashMap<String> = PerNsHashMap(this, rootModData)

    val childModules: MutableMap<String, ModData> = hashMapOf()

    /**
     * Macros visible in current module in legacy textual scope.
     * Module scoped macros will be inserted into [visibleItems] instead of here.
     * Currently stores only cfg-enabled macros.
     */
    // TODO: Custom map? (Profile memory usage)
    val legacyMacros: THashMap<String, SmartList<MacroDefInfo>> = THashMap()

    /** Explicitly declared proc macros */
    val procMacros: MutableSet<String> = hashSetOf()

    /** Traits imported via `use Trait as _;` */
    val unnamedTraitImports: MutableMap<ModPath, Visibility> = THashMap()

    /**
     * Make sense only for files ([isRsFile] == true).
     * Value `false` means that `this` is not accessible from [CrateDefMap.root] through [ModData.childModules],
     * but can be accessible using [CrateDefMap.fileInfos].
     * It could happen when two mod declarations with same path has different cfg-attributes.
     */
    var isShadowedByOtherFile: Boolean = true

    fun getVisibleItem(name: String): PerNs = visibleItems.getOrDefault(name, PerNs.Empty)

    fun getVisibleItems(filterVisibility: (Visibility) -> Boolean): List<Pair<String, PerNs>> {
        val usualItems = visibleItems.entries
            .map { (name, visItem) -> name to visItem.filterVisibility(filterVisibility) }
            .filterNot { (_, visItem) -> visItem.isEmpty }
        if (unnamedTraitImports.isEmpty()) return usualItems

        val traitItems = unnamedTraitImports
            .mapNotNull { (path, visibility) ->
                if (!filterVisibility(visibility)) return@mapNotNull null
                val trait = VisItem(path, visibility, isModOrEnum = false)
                "_" to PerNs(types = trait)
            }
        return usualItems + traitItems
    }

    /** Returns true if [visibleItems] were changed */
    fun addVisibleItem(name: String, def: PerNs): Boolean =
        pushResolutionFromImport(this, name, def, ImportType.NAMED)

    fun asVisItem(): VisItem {
        val parent = parent ?: error("Use CrateDefMap.rootAsPerNs for root ModData")
        return parent.visibleItems[name]?.types?.takeIf { it.isModOrEnum }
            ?: error("Inconsistent `visibleItems` and `childModules` in parent of $this")
    }

    fun asPerNs(): PerNs = PerNs(types = asVisItem())

    fun getNthParent(n: Int): ModData? {
        check(n >= 0)
        var current = this
        repeat(n) {
            current = current.parent ?: return null
        }
        return current
    }

    fun addLegacyMacro(name: String, defInfo: MacroDefInfo) {
        val existing = legacyMacros.putIfAbsent(name, SmartList(defInfo)) ?: return
        existing += defInfo
    }

    fun addLegacyMacros(defs: Map<String, MacroDefInfo>) {
        for ((name, def) in defs) {
            addLegacyMacro(name, def)
        }
    }

    fun visitDescendants(visitor: (ModData) -> Unit) {
        visitor(this)
        for (childMod in childModules.values) {
            childMod.visitDescendants(visitor)
        }
    }

    override fun toString(): String = "ModData($path, crate=$crateDescription)"
}

data class PerNs(
    val types: VisItem? = null,
    val values: VisItem? = null,
    val macros: VisItem? = null,
) {
    val isEmpty: Boolean get() = types == null && values == null && macros == null

    constructor(visItem: VisItem, ns: Set<Namespace>) :
        this(
            visItem.takeIf { Namespace.Types in ns },
            visItem.takeIf { Namespace.Values in ns },
            visItem.takeIf { Namespace.Macros in ns }
        )

    fun adjust(visibility: Visibility, isFromNamedImport: Boolean): PerNs =
        PerNs(
            types?.adjust(visibility, isFromNamedImport),
            values?.adjust(visibility, isFromNamedImport),
            macros?.adjust(visibility, isFromNamedImport)
        )

    fun filterVisibility(filter: (Visibility) -> Boolean): PerNs =
        PerNs(
            types?.takeIf { filter(it.visibility) },
            values?.takeIf { filter(it.visibility) },
            macros?.takeIf { filter(it.visibility) }
        )

    fun or(other: PerNs): PerNs {
        if (isEmpty) return other
        if (other.isEmpty) return this
        return PerNs(
            types.or(other.types),
            values.or(other.values),
            macros.or(other.macros)
        )
    }

    private fun VisItem?.or(other: VisItem?): VisItem? {
        if (this == null) return other
        if (other == null) return this
        if (visibility == CfgDisabled && other.visibility != CfgDisabled) return other
        if (visibility == Invisible && !other.visibility.isInvisible) return other
        return this
    }

    fun mapItems(f: (VisItem) -> VisItem): PerNs =
        PerNs(
            types?.let { f(it) },
            values?.let { f(it) },
            macros?.let { f(it) }
        )

    companion object {
        val Empty: PerNs = PerNs()
    }
}

/**
 * The item which can be visible in the module (either directly declared or imported)
 * Could be [RsEnumVariant] (because it can be imported)
 */
data class VisItem(
    /**
     * Full path to item, including its name.
     * Note: Can't store [containingMod] and [name] separately, because [VisItem] could be used for crate root
     */
    val path: ModPath,
    val visibility: Visibility,
    val isModOrEnum: Boolean = false,
    /**
     * Records whether this item was added to mod scope with named or glob import.
     * Needed to determine whether we can override it (usual imports overrides glob-imports).
     * Used only in [DefCollector], but stored here as an optimization.
     */
    val isFromNamedImport: Boolean = true,
) {
    init {
        check(isModOrEnum || path.segments.isNotEmpty())
    }

    /** Mod where item is explicitly declared */
    val containingMod: ModPath get() = path.parent
    val name: String get() = path.name
    val crate: CratePersistentId get() = path.crate

    fun adjust(visibilityNew: Visibility, isFromNamedImport: Boolean): VisItem =
        copy(
            visibility = if (visibility.isInvisible) visibility else visibilityNew,
            isFromNamedImport = isFromNamedImport
        )

    override fun toString(): String = "$visibility $path"
}

sealed class Visibility {
    object Public : Visibility()

    /**
     * Includes private visibility.
     * Constructor is private because [ModData.visibilityInSelf] must be used instead.
     * For the same reason [equals] is not overridden.
     */
    class Restricted private constructor(val inMod: ModData) : Visibility() {
        companion object {
            fun create(inMod: ModData): Restricted = Restricted(inMod)
        }
    }

    /**
     * Means that we have import to private item
     * So normally we should ignore such [VisItem] (it is not accessible)
     * But we record it for completion, etc
     */
    object Invisible : Visibility()

    object CfgDisabled : Visibility()

    fun isVisibleFromOtherCrate(): Boolean = this == Public

    fun isVisibleFromMod(mod: ModData): Boolean {
        return when (this) {
            Public -> true
            // Alternative realization: `mod.parents.contains(inMod)`
            is Restricted -> inMod.path.isSubPathOf(mod.path)
            Invisible, CfgDisabled -> false
        }
    }

    fun isStrictlyMorePermissive(other: Visibility): Boolean {
        return if (this is Restricted && other is Restricted) {
            inMod.crate == other.inMod.crate
                && inMod != other.inMod
                && other.inMod.parents.contains(inMod)
        } else {
            when (this) {
                Public -> other !is Public
                is Restricted -> other == Invisible || other == CfgDisabled
                Invisible -> other == CfgDisabled
                CfgDisabled -> false
            }
        }
    }

    val isInvisible: Boolean get() = this == Invisible || this == CfgDisabled

    override fun toString(): String =
        when (this) {
            Public -> "Public"
            is Restricted -> "Restricted(in ${inMod.path})"
            Invisible -> "Invisible"
            CfgDisabled -> "CfgDisabled"
        }
}

/** Path to a module or an item in module */
class ModPath(
    val crate: CratePersistentId,
    val segments: Array<String>,
    // val fileId: FileId,  // id of containing file
    // val fileRelativePath: String  // empty for pathRsFile
) {
    val name: String get() = segments.last()
    val parent: ModPath get() = ModPath(crate, segments.copyOfRange(0, segments.size - 1))

    fun append(segment: String): ModPath = ModPath(crate, segments + segment)

    /** `mod1::mod2` isSubPathOf `mod1::mod2::mod3` */
    fun isSubPathOf(other: ModPath): Boolean {
        if (crate != other.crate) return false

        if (segments.size > other.segments.size) return false
        for (index in segments.indices) {
            if (segments[index] != other.segments[index]) return false
        }
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ModPath
        return crate == other.crate && segments.contentEquals(other.segments)
    }

    override fun hashCode(): Int = 31 * crate + segments.contentHashCode()

    override fun toString(): String = segments.joinToString("::").ifEmpty { "crate" }
}

val RESOLVE_LOG = Logger.getInstance("org.rust.resolve2")
