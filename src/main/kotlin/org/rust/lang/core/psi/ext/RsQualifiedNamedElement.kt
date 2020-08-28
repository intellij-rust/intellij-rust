/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsQualifiedName.ChildItemType.*
import org.rust.lang.core.psi.ext.RsQualifiedName.ParentItemType.*
import org.rust.lang.core.stubs.index.ReexportKey
import org.rust.lang.core.stubs.index.RsExternCrateReexportIndex
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.ty.TyPrimitive

interface RsQualifiedNamedElement : RsNamedElement {
    val crateRelativePath: String?
}

/** Always starts with crate root name */
val RsQualifiedNamedElement.qualifiedName: String?
    get() {
        val inCratePath = crateRelativePath ?: return null
        val cargoTarget = containingCrate?.normName ?: return null
        return "$cargoTarget$inCratePath"
    }

/** Starts with 'crate' instead of crate root name if `context` is in same crate */
fun RsQualifiedNamedElement.qualifiedNameInCrate(context: RsElement): String? {
    val crateRelativePath = crateRelativePath
    if (context.crateRoot != crateRoot || crateRelativePath == null) {
        return qualifiedName
    }

    check(crateRelativePath.isEmpty() || crateRelativePath.startsWith("::"))
    return "crate$crateRelativePath"
}

/** If `this` is `crate::inner1::inner2::foo` and `context` is `crate::inner1`, then returns `inner2::foo` */
fun RsQualifiedNamedElement.qualifiedNameRelativeTo(context: RsMod): String? {
    val absolutePath = qualifiedNameInCrate(context) ?: return null
    if (!containingMod.superMods.contains(context)) return absolutePath
    return convertPathToRelativeIfPossible(context, absolutePath)
}

fun convertPathToRelativeIfPossible(context: RsMod, absolutePath: String): String {
    val contextModPath = context.crateRelativePath ?: return absolutePath
    val contextModPathPrefix = "crate$contextModPath::"
    if (!absolutePath.startsWith(contextModPathPrefix)) return absolutePath
    val relativePath = absolutePath.removePrefix(contextModPathPrefix)

    val cargoWorkspace = context.cargoWorkspace ?: return relativePath
    if (cargoWorkspace.packages.any { relativePath.startsWith("${it.normName}::") }) {
        return "self::$relativePath"
    }
    return relativePath
}

@Suppress("DataClassPrivateConstructor")
data class RsQualifiedName private constructor(
    val crateName: String,
    val modSegments: List<String>,
    val parentItem: Item,
    val childItems: List<Item>
) {

    fun toUrlPath(): String {
        val segments = mutableListOf(crateName)
        segments += modSegments
        val (pageName, anchor) = if (parentItem.type == MOD || parentItem.type == CRATE) {
            "index.html" to ""
        } else {
            "$parentItem.html" to if (childItems.isNotEmpty()) childItems.joinToString(separator = ".", prefix = "#") else ""
        }
        segments += pageName
        return segments.joinToString(separator = "/", postfix = anchor)
    }

    /** Tries to find rust qualified name element related to this qualified name */
    fun findPsiElement(psiManager: PsiManager, context: RsElement): RsQualifiedNamedElement? {
        val item = childItems.lastOrNull() ?: parentItem

        // If it's link to crate, try to find the corresponding `lib.rs` file
        if (item.type == CRATE) {
            return findCrateRoot(item, psiManager, context)
        }

        // Otherwise, split search into two steps:
        val project = context.project
        // First: look for `RsQualifiedNamedElement` with the same name as expected,
        // generate sequence of all possible reexports of this element and check
        // if any variant has the same `RsQualifiedName`
        var result = lookupInIndex(project, RsNamedElementIndex.KEY, { it }) { element ->
            if (element !is RsQualifiedNamedElement) return@lookupInIndex null
            val candidate = if (element is RsModDeclItem && item.type == MOD) {
                element.reference.resolve() as? RsMod ?: return@lookupInIndex null
            } else {
                element
            }
            QualifiedNamedItem.ExplicitItem(candidate)
        }

        if (result == null) {
            // Second: if the first step didn't find any suitable item,
            // look over all direct item reexports (considering possible new alias),
            // generate sequence of all possible reexports for each element and check
            // if any variant has the same `RsQualifiedName`
            result = lookupInIndex(project, RsReexportIndex.KEY, ReexportKey::ProducedNameKey) { useSpeck ->
                if (useSpeck.isStarImport) return@lookupInIndex null
                val candidate = useSpeck.path?.reference?.resolve() as? RsQualifiedNamedElement
                    ?: return@lookupInIndex null
                QualifiedNamedItem.ReexportedItem.from(useSpeck, candidate)
            }
        }

        return result
    }

    private fun findCrateRoot(item: Item, psiManager: PsiManager, context: RsElement): RsFile? {
        var target: CargoWorkspace.Target? = null

        loop@ for (pkg in context.cargoWorkspace?.packages.orEmpty()) {
            val libTarget = pkg.libTarget
            if (libTarget?.normName == item.name) {
                target = libTarget
                break
            } else {
                for (t in pkg.targets) {
                    if (t.normName == item.name) {
                        target = t
                        break@loop
                    }
                }
            }
        }
        val crateRoot = target?.crateRoot ?: return null
        return psiManager.findFile(crateRoot)?.rustFile
    }

    private inline fun <reified T : RsElement, K> lookupInIndex(
        project: Project,
        indexKey: StubIndexKey<K, T>,
        keyProducer: (String) -> K,
        crossinline transform: (T) -> QualifiedNamedItem?
    ): RsQualifiedNamedElement? {
        var result: RsQualifiedNamedElement? = null
        val item = childItems.lastOrNull() ?: parentItem
        StubIndex.getInstance().processElements(
            indexKey,
            keyProducer(item.name),
            project,
            GlobalSearchScope.allScope(project),
            T::class.java
        ) { element ->
            val qualifiedNamedItem = transform(element) ?: return@processElements true
            val withReexports = qualifiedNamedItem.withModuleReexports(project)
            if (withReexports.any { RsQualifiedName.from(it) == this }) {
                result = qualifiedNamedItem.item
                return@processElements false
            }
            true
        }
        return result
    }

    companion object {

        private val LOG: Logger = Logger.getInstance(RsQualifiedName::class.java)

        @JvmStatic
        fun from(path: String): RsQualifiedName? {
            val segments = path.split("/")
            if (segments.size < 2) return null

            val (parentItem, childItems) = if (segments.size == 2 && segments[1] == "index.html") {
                Item(segments[0], CRATE) to emptyList()
            } else {
                // Last segment contains info about item type and name
                // and it should have the following structure:
                // parentItem ( '#' childItem (. childItem)? )?
                val itemParts = segments.last().split("#")
                val parentRaw = itemParts[0]
                val childrenRaw = itemParts.getOrNull(1)

                val parentItem = parentItem(segments[segments.lastIndex - 1], parentRaw) ?: return null
                val childItems = if (childrenRaw != null) {
                    childItems(childrenRaw) ?: return null
                } else {
                    emptyList()
                }
                parentItem to childItems
            }

            return RsQualifiedName(segments[0], segments.subList(1, segments.lastIndex), parentItem, childItems)
        }

        private fun parentItem(prevSegment: String, raw: String): Item? {
            if (raw == "index.html") {
                return Item(prevSegment, MOD)
            }
            val parts = raw.split(".")
            // We suppose that string representation of parent item has the following structure:
            // type.Name.html
            if (parts.size != 3 || parts.last() != "html") return null
            val type = ParentItemType.fromString(parts[0]) ?: return null
            return Item(parts[1], type)
        }

        private fun childItems(raw: String): List<Item>? {
            val parts = raw.split(".")
            if (parts.size != 2 && parts.size != 4) return null

            return parts.windowed(size = 2, step = 2).map { (type, name) ->
                val itemType = ChildItemType.fromString(type) ?: return null
                Item(name, itemType)
            }
        }

        @JvmStatic
        fun from(element: RsQualifiedNamedElement): RsQualifiedName? {
            val parent = parentItem(element)

            val (parentItem, childItems) = if (parent != null) {
                parent to (element.toChildItems() ?: return null)
            } else {
                val parentItem = element.toParentItem() ?: return null
                parentItem to emptyList()
            }
            val crateName = if (parentItem.type == PRIMITIVE) {
                STD
            } else {
                element.containingCrate?.normName ?: return null
            }

            val modSegments = if (parentItem.type == PRIMITIVE || parentItem.type == MACRO) {
                listOf()
            } else {
                val parentElement = parentItem.element ?: return null
                val mod = parentElement as? RsMod ?: parentElement.containingMod
                mod.superMods
                    .asReversed()
                    .drop(1)
                    .map { it.modName ?: return null }
            }

            return RsQualifiedName(crateName, modSegments, parentItem, childItems)
        }

        @JvmStatic
        fun from(qualifiedNamedItem: QualifiedNamedItem): RsQualifiedName? {
            val crateName = qualifiedNamedItem.containingCrate?.normName ?: return null
            val modSegments = mutableListOf<String>()

            val (parentItem, childItem) = qualifiedNamedItem.item.toItems() ?: return null
            if (parentItem.type != MACRO) {
                qualifiedNamedItem.superMods
                    ?.asReversed()
                    ?.drop(1)
                    ?.mapTo(modSegments) { it.modName ?: return null }
                    ?: return null
            }
            if (parentItem.type == MOD) {
                modSegments += parentItem.name
            }

            return RsQualifiedName(crateName, modSegments, parentItem, childItem)
        }

        @JvmStatic
        fun from(path: RsPath): RsQualifiedName? {
            val primitiveType = TyPrimitive.fromPath(path) ?: return null
            return RsQualifiedName(STD, emptyList(), Item.primitive(primitiveType.name), emptyList())
        }

        private fun RsQualifiedNamedElement.toItems(): Pair<Item, List<Item>>? {
            val parent = parentItem(this)

            return if (parent != null) {
                parent to (toChildItems() ?: return null)
            } else {
                val parentItem = toParentItem() ?: return null
                parentItem to emptyList()
            }
        }

        private fun parentItem(element: RsQualifiedNamedElement): Item? {
            val parentItem: RsQualifiedNamedElement? = when (element) {
                is RsAbstractable -> {
                    when (val owner = element.owner) {
                        is RsAbstractableOwner.Trait -> owner.trait
                        is RsAbstractableOwner.Impl -> {
                            return owner.impl.typeReference?.toParentItem()
                        }
                        else -> return null
                    }
                }
                is RsEnumVariant -> element.parentEnum
                is RsNamedFieldDecl -> {
                    val owner = element.owner
                    (owner as? RsEnumVariant)?.parentEnum ?: owner
                }
                else -> return null
            }
            return parentItem?.toParentItem()
        }

        private fun RsTypeReference.toParentItem(): Item? {
            return when (val type = skipParens()) {
                is RsTupleType -> Item.primitive("tuple")
                is RsFnPointerType -> Item.primitive("fn")
                is RsArrayType -> Item.primitive(if (type.isSlice) "slice" else "array")
                is RsRefLikeType -> {
                    when {
                        type.isRef -> Item.primitive("reference")
                        type.isPointer -> Item.primitive("pointer")
                        else -> null
                    }

                }
                is RsBaseType -> when (val kind = type.kind) {
                    RsBaseTypeKind.Unit -> Item.primitive("unit")
                    RsBaseTypeKind.Never -> Item.primitive("never")
                    RsBaseTypeKind.Underscore -> return null
                    is RsBaseTypeKind.Path -> {
                        val path = kind.path
                        val primitiveType = TyPrimitive.fromPath(path)
                        if (primitiveType != null) return Item.primitive(primitiveType.name)
                        (path.reference?.resolve() as? RsQualifiedNamedElement)?.toParentItem()
                    }
                }
                else -> null
            }
        }

        private fun RsQualifiedNamedElement.toParentItem(): Item? {
            val name = ((this as? RsMod)?.modName ?: name) ?: return null
            val itemType = when (this) {
                is RsStructItem -> STRUCT
                is RsEnumItem -> ENUM
                is RsTraitItem -> TRAIT
                is RsTypeAlias -> TYPE
                is RsFunction -> FN
                is RsConstant -> CONSTANT
                is RsMacro, is RsMacro2 -> MACRO
                is RsMod -> {
                    if (isCrateRoot) {
                        val crateName = containingCrate?.normName ?: return null
                        return Item(crateName, CRATE, this)
                    }
                    MOD
                }
                is RsModDeclItem -> MOD
                else -> error("Unexpected type: `$this`")
            }
            return Item(name, itemType, this)
        }

        private fun RsQualifiedNamedElement.toChildItems(): List<Item>? {
            val name = name ?: return null
            val result = mutableListOf<Item>()
            val type = when (this) {
                is RsEnumVariant -> VARIANT
                is RsNamedFieldDecl -> {
                    when (val owner = owner) {
                        is RsStructItem -> STRUCTFIELD
                        is RsEnumVariant -> {
                            val variantName = owner.name ?: return null
                            result += Item(variantName, VARIANT, owner)
                            FIELD
                        }
                        else -> return null
                    }
                }
                is RsTypeAlias -> ASSOCIATEDTYPE
                is RsConstant -> ASSOCIATEDCONSTANT
                is RsFunction -> if (isAbstract) TYMETHOD else METHOD
                else -> return null
            }
            result += Item(name, type, this)
            return result
        }
    }

    class Item(val name: String, val type: ItemType, val element: RsElement? = null) {
        override fun toString(): String = "$type.$name"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Item

            if (name != other.name) return false
            if (type != other.type) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + type.hashCode()
            return result
        }

        companion object {
            fun primitive(name: String): Item = Item(name, PRIMITIVE)
        }
    }

    interface ItemType

    enum class ParentItemType : ItemType {
        STRUCT,
        ENUM,
        TRAIT,
        TYPE,
        FN,
        CONSTANT,
        MACRO,
        PRIMITIVE,
        // Synthetic types - rustdoc uses different links for mods and crates items
        // It generates `crateName/index.html` and `path/modName/index.html` links for crates and modules respectively
        // instead of `path/type.Name.html`
        MOD,
        CRATE;

        override fun toString(): String = name.toLowerCase()

        companion object {

            fun fromString(name: String): ParentItemType? {
                return when (name) {
                    "struct" -> STRUCT
                    "enum" -> ENUM
                    "trait" -> TRAIT
                    "type" -> TYPE
                    "fn" -> FN
                    "constant" -> CONSTANT
                    "macro" -> MACRO
                    "primitive" -> PRIMITIVE
                    else -> {
                        LOG.warn("Unexpected parent item type: `$name`")
                        null
                    }
                }
            }
        }
    }

    enum class ChildItemType : ItemType {
        VARIANT,
        FIELD,
        STRUCTFIELD,
        ASSOCIATEDTYPE,
        ASSOCIATEDCONSTANT,
        TYMETHOD,
        METHOD;

        override fun toString(): String = name.toLowerCase()

        companion object {
            fun fromString(name: String): ChildItemType? {
                return when (name) {
                    "variant" -> VARIANT
                    "field" -> FIELD
                    "structfield" -> STRUCTFIELD
                    "associatedtype" -> ASSOCIATEDTYPE
                    "associatedconstant" -> ASSOCIATEDCONSTANT
                    "tymethod" -> TYMETHOD
                    "method" -> METHOD
                    else -> {
                        LOG.warn("Unexpected child item type: `$name`")
                        null
                    }
                }
            }
        }
    }
}

sealed class QualifiedNamedItem(val item: RsQualifiedNamedElement) {

    abstract val itemName: String?
    abstract val isPublic: Boolean
    abstract val superMods: List<ModWithName>?
    abstract val containingCrate: Crate?

    val parentCrateRelativePath: String?
        get() {
            val path = superMods
                ?.map { it.modName ?: return null }
                ?.asReversed()
                ?.drop(1)
                ?.joinToString("::") ?: return null
            return if (item is RsEnumVariant) item.parentEnum.name?.let { "$path::$it" } else path
        }

    val crateRelativePath: String?
        get() {
            val name = itemName ?: return null
            val parentPath = parentCrateRelativePath ?: return null
            if (parentPath.isEmpty()) return name
            return "$parentPath::$name".removePrefix("::")
        }

    override fun toString(): String {
        return "${containingCrate?.normName}::$crateRelativePath"
    }

    class ExplicitItem(item: RsQualifiedNamedElement) : QualifiedNamedItem(item) {
        override val itemName: String? get() = item.name
        override val isPublic: Boolean get() = (item as? RsVisible)?.isPublic == true
        override val superMods: List<ModWithName>?
            get() = (if (item is RsMod) item.`super` else item.containingMod)?.superMods?.map { ModWithName(it) }
        override val containingCrate: Crate? get() = item.containingCrate
    }

    class ReexportedItem private constructor(
        override val itemName: String?,
        private val reexportItem: RsElement,
        item: RsQualifiedNamedElement
    ) : QualifiedNamedItem(item) {

        override val isPublic: Boolean get() = true
        override val superMods: List<ModWithName>? get() = reexportItem.containingMod.superMods.map { ModWithName(it) }
        override val containingCrate: Crate? get() = reexportItem.containingCrate

        companion object {
            fun from(useSpeck: RsUseSpeck, item: RsQualifiedNamedElement): ReexportedItem {
                return ReexportedItem(useSpeck.nameInScope, useSpeck, item)
            }

            fun from(externCrateItem: RsExternCrateItem, item: RsQualifiedNamedElement): ReexportedItem {
                return ReexportedItem(item.name, externCrateItem, item)
            }
        }
    }

    class CompositeItem(
        override val itemName: String?,
        override val isPublic: Boolean,
        private val reexportedModItem: QualifiedNamedItem,
        private val explicitSuperMods: List<ModWithName>,
        item: RsQualifiedNamedElement
    ) : QualifiedNamedItem(item) {

        override val superMods: List<ModWithName>?
            get() {
                val mods = ArrayList(explicitSuperMods)
                mods += reexportedModItem.superMods.orEmpty()
                return mods
            }
        override val containingCrate: Crate? get() = reexportedModItem.containingCrate
    }

    data class ModWithName(val modItem: RsMod, val modName: String? = modItem.modName)
}

/**
 * Collect all possible imports from original import item path using reexports of modules and wildcard reexports
 */
fun QualifiedNamedItem.withModuleReexports(project: Project): List<QualifiedNamedItem> {
    require(this !is QualifiedNamedItem.CompositeItem) {
        "`QualifiedNamedItem.withModuleReexports` shouldn't be called for `QualifiedNamedItem.CompositeItem`"
    }

    return collectImportItems(project)
}

private fun QualifiedNamedItem.collectImportItems(
    project: Project,
    // Contains already visited edges of module graph
    // (useSpeck element <-> reexport edge of module graph).
    // Only reexports can create cycles in module graph
    // so it's enough to collect only such edges
    visited: MutableSet<RsUseSpeck> = HashSet()
): List<QualifiedNamedItem> {
    val importItems = mutableListOf(this)
    val superMods = superMods.orEmpty()
    superMods.forEachIndexed { index, ancestorMod ->
        val modItem = ancestorMod.modItem
        val modOriginalName = (if (modItem.isCrateRoot) modItem.containingCrate?.normName else modItem.modName)
            ?: return@forEachIndexed

        RsReexportIndex.findReexportsByOriginalName(project, modOriginalName)
            .mapNotNull {
                if (it in visited) return@mapNotNull null
                val reexportedMod = it.pathOrQualifier?.reference?.resolve() as? RsMod
                if (reexportedMod != modItem) return@mapNotNull null
                it to reexportedMod
            }
            .forEach { (useSpeck, reexportedMod) ->
                // only public items can be reexported
                if (!useSpeck.isStarImport && !reexportedMod.isPublic) return@forEach
                visited += useSpeck
                val (mod, explicitSuperMods) = if (!useSpeck.isStarImport) {
                    // In case of general reexport
                    //
                    // ```rust
                    // mod foo {
                    //     pub use bar::baz; // <---
                    //     mod bar {
                    //         pub mod baz {
                    //             public struct Baz;
                    //         }
                    //     }
                    // }
                    // ```
                    //
                    // reexportedMod is `baz` (from `pub use bar::baz` use item).
                    // And we should generate "foo::[baz::]Baz" item from "foo::[bar::baz::]Baz"
                    //                                ^                              /
                    //                                 \____________________________/
                    //
                    // In case of reexport with alias
                    //
                    // ```rust
                    // mod foo {
                    //     pub use bar::baz as qqq; // <---
                    //     mod bar {
                    //         pub mod baz {
                    //             public struct Baz;
                    //         }
                    //     }
                    // }
                    // ```
                    //
                    // reexportedMod is `baz` with `qqq` name (from `pub use bar::baz as qqq` use item).
                    // And we should generate "foo::[qqq::]Baz" item from "foo::[bar::baz::]Baz"
                    //                                ^                              /
                    //                                 \____________________________/
                    val explicitSuperMods = superMods.subList(0, index) + QualifiedNamedItem.ModWithName(reexportedMod, useSpeck.nameInScope)
                    reexportedMod to explicitSuperMods
                } else {
                    // Otherwise (when use item has wildcard)
                    //
                    // ```rust
                    // mod foo {
                    //     pub use bar::baz::* // <---
                    //     mod bar {
                    //         pub mod baz {
                    //             public struct Baz;
                    //         }
                    //     }
                    // }
                    // ```
                    //
                    // reexportedMod is still `baz` (from `pub use bar::baz::*` use item).
                    // But we should replace it with `foo` mod because use item with wildcard reexports
                    // children items of `baz` to `foo` instead of `baz` itself,
                    // i.e. generate "foo::[]Baz" item from "foo::[bar::baz::]Baz"
                    //                     ^                              /
                    //                      \____________________________/
                    useSpeck.containingMod to superMods.subList(0, index)
                }
                val items = QualifiedNamedItem.ReexportedItem.from(useSpeck, mod).collectImportItems(project, visited)
                importItems += items.map {
                    QualifiedNamedItem.CompositeItem(itemName, isPublic, it, explicitSuperMods, item)
                }
                visited -= useSpeck
            }
    }

    return importItems.flatMap { it.withExternCrateReexports(project) }
}

private fun QualifiedNamedItem.withExternCrateReexports(project: Project): List<QualifiedNamedItem> {
    val importItems = mutableListOf(this)
    val superMods = superMods.orEmpty()
    val root = superMods.lastOrNull()?.modItem ?: return importItems
    if (!root.isCrateRoot) return importItems
    RsExternCrateReexportIndex.findReexports(project, root).forEach { externCrateItem ->
        val items = QualifiedNamedItem.ReexportedItem.from(externCrateItem, root).collectImportItems(project)
        importItems += items.map {
            val explicitSuperMods = superMods.dropLast(1) + QualifiedNamedItem.ModWithName(root, externCrateItem.nameWithAlias)
            QualifiedNamedItem.CompositeItem(itemName, isPublic, it, explicitSuperMods, item)
        }
    }
    return importItems
}
