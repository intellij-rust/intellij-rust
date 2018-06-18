/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

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

class RsQualifiedName private constructor(
    private val crateName: String,
    private val modSegments: List<String>,
    private val parentItem: Item,
    private val childItem: Item?
) {

    fun toUrlPath(): String {
        val segments = mutableListOf(crateName)
        segments += modSegments
        val (pageName, anchor) =
        if (parentItem.type == MOD) {
            "index.html" to ""
        } else {
            "$parentItem.html" to (if (childItem != null) "#$childItem" else "")
        }
        segments += pageName
        return segments.joinToString(separator = "/", postfix = anchor)
    }

    companion object {

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
            val modSegments = if (parentItem.type == ParentItemType.PRIMITIVE) {
                listOf()
            } else {
                element.containingMod.superMods
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
            val name = name ?: return null
            val itemType = when (this) {
                is RsStructItem -> STRUCT
                is RsEnumItem -> ENUM
                is RsTraitItem -> TRAIT
                is RsTypeAlias -> TYPE
                is RsFunction -> FN
                is RsConstant -> CONSTANT
                is RsMacroDefinition -> MACRO
                is RsMod,
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

    private data class Item(val name: String, val type: ItemType) {
        override fun toString(): String = "$type.$name"
    }

    private interface ItemType

    private enum class ParentItemType : ItemType {
        STRUCT,
        ENUM,
        TRAIT,
        TYPE,
        FN,
        CONSTANT,
        MACRO,
        MOD,
        PRIMITIVE;

        override fun toString(): String = name.toLowerCase()
    }

    private enum class ChildItemType : ItemType {
        VARIANT,
        STRUCTFIELD,
        ASSOCIATEDTYPE,
        ASSOCIATEDCONSTANT,
        TYMETHOD,
        METHOD;

        override fun toString(): String = name.toLowerCase()
    }
}
