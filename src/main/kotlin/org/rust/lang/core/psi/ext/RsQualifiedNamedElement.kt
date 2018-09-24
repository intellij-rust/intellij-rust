/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsQualifiedName.ChildItemType.*
import org.rust.lang.core.psi.ext.RsQualifiedName.ParentItemType.*
import org.rust.lang.core.stubs.index.RsReexportIndex
import org.rust.lang.core.types.ty.TyPrimitive

interface RsQualifiedNamedElement : RsNamedElement {
    val crateRelativePath: String?
}

val RsQualifiedNamedElement.qualifiedName: String? get() {
    val inCratePath = crateRelativePath ?: return null
    val cargoTarget = containingCargoTarget?.normName ?: return null
    return "$cargoTarget$inCratePath"
}

@Suppress("DataClassPrivateConstructor")
data class RsQualifiedName private constructor(
    val crateName: String,
    val modSegments: List<String>,
    val parentItem: Item,
    val childItem: Item?
) {

    fun toUrlPath(): String {
        val segments = mutableListOf(crateName)
        segments += modSegments
        val (pageName, anchor) = if (parentItem.type == MOD || parentItem.type == CRATE) {
            "index.html" to ""
        } else {
            "$parentItem.html" to (if (childItem != null) "#$childItem" else "")
        }
        segments += pageName
        return segments.joinToString(separator = "/", postfix = anchor)
    }

    companion object {
        
        private val LOG: Logger = Logger.getInstance(RsQualifiedName::class.java)

        @JvmStatic
        fun from(path: String): RsQualifiedName? {
            val segments = path.split("/")
            if (segments.size < 2) return null

            val (parentItem, childItem) = if (segments.size == 2 && segments[1] == "index.html") {
                Item(segments[0], ParentItemType.CRATE) to null
            } else {
                // Last segment contains info about item type and name
                // and it should have the following structure:
                // parentItem ( '#' childItem )?
                val itemParts = segments.last().split("#")
                val parentRaw = itemParts[0]
                val childRaw = itemParts.getOrNull(1)

                val parentItem = parentItem(segments[segments.lastIndex - 1], parentRaw) ?: return null
                val childItem = if (childRaw != null) {
                    childItem(childRaw) ?: return null
                } else {
                    null
                }
                parentItem to childItem
            }

            return RsQualifiedName(segments[0], segments.subList(1, segments.lastIndex), parentItem, childItem)
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
        
        private fun childItem(raw: String): Item? {
            val parts = raw.split(".")
            if (parts.size != 2) return null
            val type = ChildItemType.fromString(parts[0]) ?: return null
            return Item(parts[1], type)
        } 

        @JvmStatic
        fun from(element: RsQualifiedNamedElement): RsQualifiedName? {
            val parent = parentItem(element)

            val (parentItem, childItem) = if (parent != null) {
                parent to (element.toChildItem() ?: return null)
            } else {
                val parentItem = element.toParentItem() ?: return null
                parentItem to null
            }

            val crateName = element.containingCargoTarget?.normName ?: return null
            val modSegments = if (parentItem.type == PRIMITIVE) {
                listOf()
            } else {
                val mod = element as? RsMod ?: element.containingMod
                mod.superMods
                    .asReversed()
                    .drop(1)
                    .map { it.modName ?: return null }
            }

            return RsQualifiedName(crateName, modSegments, parentItem, childItem)
        }

        private fun parentItem(element: RsQualifiedNamedElement): Item? {
            val parentItem: RsQualifiedNamedElement? = when (element) {
                is RsAbstractable -> {
                    val owner = element.owner
                    when (owner) {
                        is RsAbstractableOwner.Trait -> owner.trait
                        is RsAbstractableOwner.Impl -> {
                            return owner.impl.typeReference?.toParentItem()
                        }
                        else -> return null
                    }
                }
                is RsEnumVariant -> element.parentEnum
                is RsFieldDecl -> element.parentStruct
                else -> return null
            }
            return parentItem?.toParentItem()
        }

        private fun RsTypeReference.toParentItem(): Item? {
            val type = typeElement
            return when (type) {
                is RsTupleType -> Item("tuple", PRIMITIVE)
                is RsFnPointerType -> Item("fn", PRIMITIVE)
                is RsArrayType -> Item(if (type.isSlice) "slice" else "array", PRIMITIVE)
                is RsRefLikeType -> {
                    when {
                        type.isRef -> Item("reference", PRIMITIVE)
                        type.isPointer -> Item("pointer", PRIMITIVE)
                        else -> null
                    }

                }
                is RsBaseType -> {
                    when {
                        type.isNever -> Item("never", PRIMITIVE)
                        type.isUnit -> Item("unit", PRIMITIVE)
                        else -> {
                            val path = type.path ?: return null
                            val primitiveType = TyPrimitive.fromPath(path)
                            if (primitiveType != null) return Item(primitiveType.name, PRIMITIVE)
                            (path.reference.resolve() as? RsQualifiedNamedElement)?.toParentItem()
                        }
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
                is RsMacro -> MACRO
                is RsMod -> {
                    if (isCrateRoot) {
                        val crateName = containingCargoTarget?.normName ?: return null
                        return Item(crateName, CRATE)
                    }
                    MOD
                }
                is RsModDeclItem -> MOD
                else -> error("Unexpected type: `$this`")
            }
            return Item(name, itemType)
        }

        private fun RsQualifiedNamedElement.toChildItem(): Item? {
            val name = name ?: return null
            val type = when (this) {
                is RsEnumVariant -> VARIANT
                is RsFieldDecl -> if (parentStruct != null) STRUCTFIELD else return null
                is RsTypeAlias -> ASSOCIATEDTYPE
                is RsConstant -> ASSOCIATEDCONSTANT
                is RsFunction -> if (isAbstract) TYMETHOD else METHOD
                else -> return null
            }
            return Item(name, type)
        }
    }

    data class Item(val name: String, val type: ItemType) {
        override fun toString(): String = "$type.$name"
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
    abstract val superMods: List<RsMod>?
    abstract val containingCargoTarget: CargoWorkspace.Target?

    val parentCrateRelativePath: String? get() {
        val path = superMods
            ?.map { it.modName ?: return null }
            ?.asReversed()
            ?.drop(1)
            ?.joinToString("::") ?: return null
        return if (item is RsEnumVariant) item.parentEnum.name?.let { "$path::$it" } else path
    }

    val crateRelativePath: String? get() {
        val name = itemName ?: return null
        val parentPath = parentCrateRelativePath ?: return null
        if (parentPath.isEmpty()) return name
        return "$parentPath::$name"
    }

    class ExplicitItem(item: RsQualifiedNamedElement) : QualifiedNamedItem(item) {
        override val itemName: String? get() = item.name
        override val isPublic: Boolean get() = (item as? RsVisible)?.isPublic == true
        override val superMods: List<RsMod>? get() = (if (item is RsMod) item.`super` else item.containingMod)?.superMods
        override val containingCargoTarget: CargoWorkspace.Target? get() = item.containingCargoTarget
    }

    class ReexportedItem(
        private val useSpeck: RsUseSpeck,
        item: RsQualifiedNamedElement
    ) : QualifiedNamedItem(item) {

        override val itemName: String? get() = useSpeck.nameInScope
        override val isPublic: Boolean get() = true
        override val superMods: List<RsMod>? get() = useSpeck.containingMod.superMods
        override val containingCargoTarget: CargoWorkspace.Target? get() = useSpeck.containingCargoTarget
    }

    class CompositeItem(
        override val itemName: String?,
        override val isPublic: Boolean,
        private val reexportedModItem: QualifiedNamedItem,
        private val explicitSuperMods: List<RsMod>,
        item: RsQualifiedNamedElement
    ) : QualifiedNamedItem(item) {

        override val superMods: List<RsMod>? get() {
            val mods = ArrayList(explicitSuperMods)
            mods += reexportedModItem.superMods.orEmpty()
            return mods
        }
        override val containingCargoTarget: CargoWorkspace.Target? get() = reexportedModItem.containingCargoTarget
    }
}

/**
 * Collect all possible imports using reexports of modules from original import item path
 */
fun QualifiedNamedItem.withModuleReexports(project: Project): List<QualifiedNamedItem> {
    check(this is QualifiedNamedItem.ExplicitItem || this is QualifiedNamedItem.ReexportedItem) {
        "`QualifiedNamedItem.withModuleReexports` should be called only for `QualifiedNamedItem.ExplicitItem` and `QualifiedNamedItem.ReexportedItem`"
    }

    // Contains already visited edges of module graph
    // (useSpeck element <-> reexport edge of module graph).
    // Only reexports can create cycles in module graph
    // so it's enough to collect only such edges
    val visited: MutableSet<RsUseSpeck> = HashSet()

    fun QualifiedNamedItem.collectImportItems(): List<QualifiedNamedItem> {
        val importItems = mutableListOf(this)
        val superMods = superMods.orEmpty()
        superMods
            // only public items can be reexported
            .filter { it.isPublic }
            .forEachIndexed { index, ancestorMod ->
                val modName = ancestorMod.modName ?: return@forEachIndexed
                RsReexportIndex.findReexportsByName(project, modName)
                    .mapNotNull {
                        if (it in visited) return@mapNotNull null
                        val reexportedMod = it.path?.reference?.resolve() as? RsMod
                        if (reexportedMod != ancestorMod) return@mapNotNull null
                        it to reexportedMod
                    }
                    .forEach { (useSpeck, reexportedMod) ->
                        visited += useSpeck
                        val items = QualifiedNamedItem.ReexportedItem(useSpeck, reexportedMod).collectImportItems()
                        importItems += items.map {
                            QualifiedNamedItem.CompositeItem(itemName, isPublic, it, superMods.subList(0, index + 1), item)
                        }
                        visited -= useSpeck
                    }
            }
        return importItems
    }

    return collectImportItems()
}
