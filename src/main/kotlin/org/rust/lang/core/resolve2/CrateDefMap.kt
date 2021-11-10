/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.util.containers.map2Array
import gnu.trove.THashMap
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.resolve.Namespace
import org.rust.lang.core.resolve2.Visibility.*
import org.rust.lang.core.resolve2.util.GlobImportGraph
import org.rust.lang.core.resolve2.util.PerNsHashMap
import org.rust.lang.core.resolve2.util.SmartListMap
import org.rust.openapiext.fileId
import org.rust.stdext.HashCode
import org.rust.stdext.writeVarInt
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Path
import java.util.*

class CrateDefMap(
    val crate: CratePersistentId,
    val root: ModData,

    // TODO: Not used after [CrateDefMap] was built, probably it is worth to move it to [CollectorContext]
    /** Used only by `extern crate crate_name;` declarations */
    val directDependenciesDefMaps: Map<String, CrateDefMap>,
    private val allDependenciesDefMaps: Map<CratePersistentId, CrateDefMap>,
    initialExternPrelude: Map<String, CrateDefMap>,
    val metaData: CrateMetaData,
    /** Equal to `root.macroIndex.single()` */
    val rootModMacroIndex: Int,
    /** Attributes of root module */
    val stdlibAttributes: RsFile.Attributes,
    /** Only for debug */
    val crateDescription: String,
) {
    /** The prelude module for this crate. See [injectPrelude] */
    var prelude: ModData? = null

    /** It is needed at least to handle `extern crate name as alias;` */
    val externPrelude: MutableMap<String, CrateDefMap> = initialExternPrelude.toMap(hashMapOf())

    /** List of `extern crate` declarations in crate root. Needed only for 2015 edition. */
    val externCratesInRoot: MutableMap<String, CrateDefMap> = hashMapOf()

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

    val timestamp: Long = System.nanoTime()

    /** Stored as memory optimization */
    val rootAsPerNs: PerNs = PerNs.types(VisItem(root.path, Public, true))

    val globImportGraph: GlobImportGraph = GlobImportGraph()

    val isAtLeastEdition2018: Boolean
        get() = metaData.edition >= Edition.EDITION_2018

    fun getDefMap(crate: CratePersistentId): CrateDefMap? =
        if (crate == this.crate) this else allDependenciesDefMaps[crate]

    fun getModData(path: ModPath): ModData? {
        val defMap = getDefMap(path.crate) ?: error("Can't find ModData for path $path")
        return defMap.root.getChildModData(path.segments)
    }

    // TODO: Possible optimization - store in [CrateDefMap] map from [String] (mod path) to [ModData]
    fun tryCastToModData(types: VisItem): ModData? {
        if (!types.isModOrEnum) return null
        return getModData(types.path)
    }

    fun getModData(mod: RsMod): ModData? {
        if (mod is RsFile) {
            val virtualFile = mod.originalFile.virtualFile ?: return null
            if (virtualFile !is VirtualFileWithId) return null
            val fileInfo = fileInfos[virtualFile.fileId]
            return fileInfo?.modData
        }
        val parentMod = mod.`super` ?: return null
        val parentModData = getModData(parentMod) ?: return null
        return parentModData.childModules[mod.modName]
    }

    fun getMacroInfo(macroDef: VisItem): MacroDefInfo? {
        val defMap = getDefMap(macroDef.crate) ?: return null
        return defMap.doGetMacroInfo(macroDef)
    }

    private fun doGetMacroInfo(macroDef: VisItem): MacroDefInfo? {
        val containingMod = getModData(macroDef.containingMod) ?: return null
        val procMacroKind = containingMod.procMacros[macroDef.name]
        if (procMacroKind != null) {
            val knownKind = getHardcodeProcMacroProperties(metaData.name, macroDef.name)
            return ProcMacroDefInfo(containingMod.crate, macroDef.path, procMacroKind, metaData.procMacroArtifact, knownKind)
        }
        containingMod.macros2[macroDef.name]?.let {
            return it
        }
        val macroInfos = containingMod.legacyMacros[macroDef.name]
            ?: error("Can't find definition for macro $macroDef")
        return macroInfos.filterIsInstance<DeclMacroDefInfo>().singlePublicOrFirst()
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
            for (macroDef in def.macros) {
                // TODO: DeclMacro2DefInfo
                val macroInfo = from.getMacroInfo(macroDef) ?: continue
                root.addLegacyMacro(name, macroInfo)
            }
        }
    }

    fun addVisitedFile(file: RsFile, modData: ModData, fileHash: HashCode) {
        val fileId = file.virtualFile.fileId
        // TODO: File included in module tree multiple times ?
        // testAssert { fileId !in fileInfos }
        val existing = fileInfos[fileId]
        if (existing != null && !modData.isDeeplyEnabledByCfg && existing.modData.isDeeplyEnabledByCfg) return
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
    fun append(index: MacroIndex): MacroIndex = MacroIndex(indices + index.indices)

    override fun compareTo(other: MacroIndex): Int = Arrays.compare(indices, other.indices)

    @Throws(IOException::class)
    fun writeTo(data: DataOutput) {
        data.writeVarInt(indices.size)
        for (index in indices) {
            data.writeVarInt(index)
        }
    }

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
    val fileId: FileId?,
    // TODO: Possible optimization - store as Array<String>
    /** Starts with :: */
    val fileRelativePath: String,
    /** `fileId` of owning directory */
    val ownedDirectoryId: FileId?,
    val hasPathAttribute: Boolean,
    val hasMacroUse: Boolean,
    val isEnum: Boolean = false,
    /** Normal cargo crate with physical crate root, etc */
    val isNormalCrate: Boolean = true,
    /** not-null when `this` corresponds to [RsBlock]. See [getHangingModInfo] */
    val context: ModData? = null,
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
     * Currently, stores only cfg-enabled macros.
     */
    val legacyMacros: SmartListMap<String, MacroDefInfo> = SmartListMap()

    /** Explicitly declared macros 2.0 (`pub macro $name ...`) */
    val macros2: MutableMap<String, DeclMacro2DefInfo> = THashMap()

    /** Explicitly declared proc macros */
    val procMacros: MutableMap<String, RsProcMacroKind> = hashMapOf()

    /** Traits imported via `use Trait as _;` */
    val unnamedTraitImports: MutableMap<ModPath, Visibility> = THashMap()

    /**
     * Make sense only for files ([isRsFile] == true).
     * Value `false` means that `this` is not accessible from [CrateDefMap.root] through [ModData.childModules],
     * but can be accessible using [CrateDefMap.fileInfos].
     * It could happen when two mod declarations with same path has different cfg-attributes.
     */
    var isShadowedByOtherFile: Boolean = true

    lateinit var asVisItem: VisItem

    var directoryContainedAllChildFiles: FileId? = if (isNormalCrate) {
        ownedDirectoryId ?: parent!!.directoryContainedAllChildFiles
    } else {
        null
    }

    /**
     * Means that mod declaration has path attribute or any parent inline mod
     * (that is mod which is inside the file containing mod decl) has path attribute
     */
    val hasPathAttributeRelativeToParentFile: Boolean
        get() = when {
            parent == null -> false
            parent.isRsFile -> hasPathAttribute
            else -> parent.hasPathAttributeRelativeToParentFile || hasPathAttribute
        }

    val timestamp: Long = System.nanoTime()

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
                "_" to PerNs.types(trait)
            }
        return usualItems + traitItems
    }

    /** Returns true if [visibleItems] were changed */
    fun addVisibleItem(name: String, def: PerNs): Boolean =
        pushResolutionFromImport(this, name, def)

    fun asVisItem(): VisItem {
        if (isCrateRoot) error("Use CrateDefMap.rootAsPerNs for root ModData")
        return asVisItem
    }

    fun asPerNs(): PerNs = context?.asPerNs() ?: PerNs.types(asVisItem())

    fun getChildModData(relativePath: Array<String>): ModData? =
        relativePath.fold(this as ModData?) { modData, segment ->
            modData?.childModules?.get(segment)
        }

    fun getNthParent(n: Int): ModData? {
        check(n >= 0)
        var current = this
        repeat(n) {
            current = current.parent ?: return null
        }
        return current
    }

    fun addLegacyMacro(name: String, defInfo: MacroDefInfo) {
        legacyMacros.addValue(name, defInfo)
    }

    fun addLegacyMacros(defs: Map<String, DeclMacroDefInfo>) {
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

    fun recordChildFileInUnusualLocation(childFileId: FileId) {
        val persistentFS = PersistentFS.getInstance()
        val childFile = persistentFS.findFileById(childFileId) ?: return
        val childDirectory = childFile.parent ?: return
        val containedDirectory = persistentFS.findFileById(directoryContainedAllChildFiles ?: return) ?: return
        if (!VfsUtil.isAncestor(containedDirectory, childDirectory, false)) {
            VfsUtil.getCommonAncestor(containedDirectory, childDirectory)?.let {
                directoryContainedAllChildFiles = it.fileId
            }
        }
    }

    override fun toString(): String = "ModData($path, crate=$crateDescription)"
}

class PerNs(
    val types: Array<VisItem> = VisItem.EMPTY_ARRAY,
    val values: Array<VisItem> = VisItem.EMPTY_ARRAY,
    val macros: Array<VisItem> = VisItem.EMPTY_ARRAY,
) {

    val isEmpty: Boolean get() = types.isEmpty() && values.isEmpty() && macros.isEmpty()
    val hasAllNamespaces: Boolean get() = types.isNotEmpty() && values.isNotEmpty() && macros.isNotEmpty()

    fun adjust(visibility: Visibility, isFromNamedImport: Boolean): PerNs =
        PerNs(
            types.map2Array { it.adjust(visibility, isFromNamedImport) },
            values.map2Array { it.adjust(visibility, isFromNamedImport) },
            macros.map2Array { it.adjust(visibility, isFromNamedImport) },
        )

    fun filterVisibility(filter: (Visibility) -> Boolean): PerNs =
        PerNs(
            types.filter { filter(it.visibility) }.toTypedArray(),
            values.filter { filter(it.visibility) }.toTypedArray(),
            macros.filter { filter(it.visibility) }.toTypedArray(),
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

    private fun Array<VisItem>.or(other: Array<VisItem>): Array<VisItem> {
        if (isEmpty()) return other
        if (other.isEmpty()) return this

        val thisVisibilityType = visibilityType()
        val otherVisibilityType = other.visibilityType()
        return if (otherVisibilityType.isWider(thisVisibilityType)) other else this
    }

    fun mapItems(f: (VisItem) -> VisItem): PerNs =
        PerNs(
            types.map2Array(f),
            values.map2Array(f),
            macros.map2Array(f),
        )

    /**
     * - Keeps only items with greatest visibility (e.g. [Invisible] and [Public] -> keep only [Public]).
     * - If all items has [CfgDisabled] visibility, keep only one item for performance reasons.
     */
    fun adjustMultiresolve(): PerNs {
        if (types.size <= 1 && values.size <= 1 && macros.size <= 1) return this

        fun Array<VisItem>.adjustMultiresolve(): Array<VisItem> {
            if (size <= 1) return this
            val visibilityType = map2Array { it.visibility.type }.maxOrNull()!!
            if (visibilityType == VisibilityType.CfgDisabled) return arrayOf(first())
            return filter { it.visibility.type == visibilityType }.toTypedArray()
        }
        return PerNs(
            types.adjustMultiresolve(),
            values.adjustMultiresolve(),
            macros.adjustMultiresolve(),
        )
    }

    fun getVisItems(namespace: Namespace): Array<VisItem> = when (namespace) {
        Namespace.Types -> types
        Namespace.Values -> values
        Namespace.Macros -> macros
        Namespace.Lifetimes -> emptyArray()
    }

    fun getVisItemsByNamespace(): Array<Pair<Array<VisItem>, Namespace>> =
        arrayOf(
            types to Namespace.Types,
            values to Namespace.Values,
            macros to Namespace.Macros,
        )

    /** Needed to compare [PartialResolvedImport] in [DefCollector.resolveImports] */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as PerNs
        if (!types.contentEquals(other.types)) return false
        if (!values.contentEquals(other.values)) return false
        if (!macros.contentEquals(other.macros)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = types.contentHashCode()
        result = 31 * result + values.contentHashCode()
        result = 31 * result + macros.contentHashCode()
        return result
    }

    companion object {
        val Empty: PerNs = PerNs(VisItem.EMPTY_ARRAY, VisItem.EMPTY_ARRAY, VisItem.EMPTY_ARRAY)

        fun from(visItem: VisItem, ns: Set<Namespace>): PerNs {
            val visItemList = arrayOf(visItem)
            return PerNs(
                if (Namespace.Types in ns) visItemList else VisItem.EMPTY_ARRAY,
                if (Namespace.Values in ns) visItemList else VisItem.EMPTY_ARRAY,
                if (Namespace.Macros in ns) visItemList else VisItem.EMPTY_ARRAY
            )
        }

        fun types(visItem: VisItem): PerNs = PerNs(types = arrayOf(visItem))
        fun macros(visItem: VisItem): PerNs = PerNs(macros = arrayOf(visItem))
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
    val isCrateRoot: Boolean get() = path.segments.isEmpty()

    fun adjust(visibilityNew: Visibility, isFromNamedImport: Boolean): VisItem =
        copy(
            visibility = visibilityNew.intersect(visibility),
            isFromNamedImport = isFromNamedImport
        )

    override fun toString(): String = "$visibility $path"

    companion object {
        val EMPTY_ARRAY: Array<VisItem> = arrayOf()
    }
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

    fun intersect(other: Visibility): Visibility = if (isStrictlyMorePermissive(other)) other else this

    val type: VisibilityType
        get() = when (this) {
            Public -> VisibilityType.Normal
            is Restricted -> VisibilityType.Normal
            Invisible -> VisibilityType.Invisible
            CfgDisabled -> VisibilityType.CfgDisabled
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

enum class VisibilityType {
    CfgDisabled,
    Invisible,
    Normal;

    fun isWider(other: VisibilityType): Boolean = ordinal > other.ordinal
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
    fun isSubPathOf(child: ModPath): Boolean {
        if (crate != child.crate) return false

        if (segments.size > child.segments.size) return false
        for (index in segments.indices) {
            if (segments[index] != child.segments[index]) return false
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

val RESOLVE_LOG: Logger = Logger.getInstance("org.rust.resolve2")
