/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.diagnostic.Logger
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsQualifiedName.ChildItemType.*
import org.rust.lang.core.psi.ext.RsQualifiedName.ParentItemType.*
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
