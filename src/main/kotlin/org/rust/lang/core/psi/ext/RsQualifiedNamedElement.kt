/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin.STDLIB_DEPENDENCY
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsQualifiedName.ChildItemType.*
import org.rust.lang.core.psi.ext.RsQualifiedName.ParentItemType.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.doc.documentationAsHtml

interface RsQualifiedNamedElement : RsNamedElement {
    val crateRelativePath: String?
}

/** Always starts with crate root name */
val RsQualifiedNamedElement.qualifiedName: String?
    get() {
        val inCratePath = crateRelativePath ?: return null
        val cargoTarget = containingCrate.asNotFake?.normName ?: return null
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

    private val itemType: ItemType get() = (childItems.lastOrNull() ?: parentItem).type

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
        /** `this` is absolute path even for relative links, conversion is done in [documentationAsHtml] */
        val crateRoot = findCrateRoot(crateName, psiManager, context) ?: return null
        return when (itemType) {
            CRATE -> crateRoot
            FIELD, STRUCTFIELD -> resolveToField(crateRoot)
            else -> resolveToPathBasedElement(crateRoot)
        }
    }

    /** It can be used for any element that can be represented as some valid Rust path unlike field */
    private fun resolveToPathBasedElement(crateRoot: RsFile): RsQualifiedNamedElement? {
        val itemType = itemType
        val segments = buildList {
            add("crate")
            addAll(modSegments)
            if (itemType != MOD) add(parentItem.name)
            addAll(childItems.map { it.name })
        }
        val pathText = segments.joinToString("::")
        val ns = itemType.namespace() ?: return null
        val path = RsCodeFragmentFactory(crateRoot.project).createPath(pathText, crateRoot, ns = ns) ?: return null
        val results = path.reference?.multiResolve()?.filterIsInstance<RsQualifiedNamedElement>().orEmpty()
        return results.firstOrNull {
            when (itemType) {
                is ChildItemType -> itemType == it.childItemType()
                is ParentItemType -> itemType == it.parentItemType()
            }
        }
    }

    private fun resolveToField(crateRoot: RsFile): RsNamedFieldDecl? {
        val ownerItem = copy(childItems = childItems.dropLast(1))
        val owner = ownerItem.resolveToPathBasedElement(crateRoot) as? RsFieldsOwner ?: return null
        val fieldItem = childItems.last()
        // Note that currently tuple fields are not supported
        // (Need to adjust API, [RsTupleFieldDecl] doesn't implement [RsQualifiedNamedElement])
        val fields = owner.namedFields
        return fields.firstOrNull { it.name == fieldItem.name }
    }

    private fun findCrateRoot(crateName: String, psiManager: PsiManager, context: RsElement): RsFile? {
        var target: CargoWorkspace.Target? = null

        loop@ for (pkg in context.cargoWorkspace?.packages.orEmpty()) {
            val libTarget = pkg.libTarget
            if (libTarget?.normName == crateName) {
                // there is fake std crate used as placeholder for dependencies of std
                if (pkg.origin == STDLIB_DEPENDENCY && crateName in listOf("std", "core", "alloc")) continue

                target = libTarget
                break
            } else {
                for (t in pkg.targets) {
                    if (t.normName == crateName) {
                        target = t
                        break@loop
                    }
                }
            }
        }
        val crateRoot = target?.crateRoot ?: return null
        return psiManager.findFile(crateRoot)?.rustFile
    }

    companion object {
        private val LOG: Logger = logger<RsQualifiedName>()

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
            val parentType = parentItem.type
            val crateName = if (parentType == PRIMITIVE || parentType == KEYWORD) {
                STD
            } else {
                element.containingCrate.asNotFake?.normName ?: return null
            }

            val parentElement = parentItem.element
            val withoutModSegments = parentType == PRIMITIVE ||
                parentType == KEYWORD ||
                parentType == MACRO && parentElement is RsMacro
            val modSegments = if (withoutModSegments) {
                emptyList()
            } else {
                if (parentElement == null) return null
                val mod = parentElement as? RsMod ?: parentElement.containingMod
                mod.superMods
                    .asReversed()
                    .drop(1)
                    .map { it.modName ?: return null }
            }

            return RsQualifiedName(crateName, modSegments, parentItem, childItems)
        }

        @JvmStatic
        fun from(element: PsiElement): RsQualifiedName? {
            return when {
                element is RsPath -> {
                    val primitiveType = TyPrimitive.fromPath(element) ?: return null
                    RsQualifiedName(STD, emptyList(), Item.primitive(primitiveType.name), emptyList())
                }
                element.isKeywordLike() -> {
                    return RsQualifiedName(STD, emptyList(), Item.keyword(element.text), emptyList())
                }
                else -> null
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
                is RsUnitType -> Item.primitive("unit")
                is RsNeverType -> Item.primitive("never")
                is RsInferType -> return null
                is RsPathType -> {
                    val path = type.path
                    val primitiveType = TyPrimitive.fromPath(path)
                    if (primitiveType != null) return Item.primitive(primitiveType.name)
                    (path.reference?.resolve() as? RsQualifiedNamedElement)?.toParentItem()
                }
                else -> null
            }
        }

        private fun RsQualifiedNamedElement.toParentItem(): Item? {
            val itemType = parentItemType() ?: return null

            val name = when (itemType) {
                CRATE -> containingCrate.asNotFake?.normName
                MOD -> (this as? RsMod)?.modName
                else -> name
            } ?: return null

            return Item(name, itemType, this)
        }

        private fun RsQualifiedNamedElement.parentItemType(): ParentItemType? =
            when (this) {
                is RsStructItem -> when (kind) {
                    RsStructKind.STRUCT -> STRUCT
                    RsStructKind.UNION -> UNION
                }
                is RsEnumItem -> ENUM
                is RsTraitItem -> TRAIT
                is RsTraitAlias -> TRAITALIAS
                is RsTypeAlias -> TYPE
                is RsFunction -> FN
                is RsConstant -> CONSTANT
                is RsMacro, is RsMacro2 -> MACRO
                is RsMod -> if (isCrateRoot) CRATE else MOD
                is RsModDeclItem -> MOD
                else -> {
                    LOG.warn("Unexpected type: `$this`")
                    null
                }
            }

        private fun RsQualifiedNamedElement.toChildItems(): List<Item>? {
            val name = name ?: return null
            val result = mutableListOf<Item>()
            val type = childItemType() ?: return null
            if (type == FIELD) {
                /** It is the only case when [childItemType] returns [FIELD] */
                check(this is RsNamedFieldDecl)
                val owner = owner as RsEnumVariant
                val variantName = owner.name ?: return null
                result += Item(variantName, VARIANT, owner)
            }
            result += Item(name, type, this)
            return result
        }

        private fun RsQualifiedNamedElement.childItemType(): ChildItemType? =
            when (this) {
                is RsEnumVariant -> VARIANT
                is RsNamedFieldDecl -> {
                    when (owner) {
                        is RsStructItem -> STRUCTFIELD
                        is RsEnumVariant -> FIELD
                        else -> null
                    }
                }
                is RsTypeAlias -> ASSOCIATEDTYPE
                is RsConstant -> ASSOCIATEDCONSTANT
                is RsFunction -> if (isAbstract) TYMETHOD else METHOD
                else -> null
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
            fun keyword(name: String): Item = Item(name, KEYWORD)
        }
    }

    sealed interface ItemType

    enum class ParentItemType : ItemType {
        STRUCT,
        UNION,
        ENUM,
        TRAIT,
        TRAITALIAS,
        TYPE,
        FN,
        CONSTANT,
        MACRO,
        PRIMITIVE,
        KEYWORD,

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
                    "union" -> UNION
                    "enum" -> ENUM
                    "trait" -> TRAIT
                    "traitalias" -> TRAITALIAS
                    "type" -> TYPE
                    "fn" -> FN
                    "constant" -> CONSTANT
                    "macro" -> MACRO
                    "primitive" -> PRIMITIVE
                    "keyword" -> KEYWORD
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

    private fun ItemType.namespace(): Set<Namespace>? =
        when (this) {
            // parent item type
            STRUCT -> TYPES_N_VALUES
            UNION, ENUM, TRAIT, TRAITALIAS, TYPE, MOD, CRATE -> TYPES
            FN, CONSTANT -> VALUES
            MACRO -> MACROS  // including bang proc macros
            PRIMITIVE, KEYWORD -> null

            // child item type
            VARIANT -> TYPES_N_VALUES
            FIELD, STRUCTFIELD -> error("fields are handled separately")
            ASSOCIATEDTYPE -> TYPES
            ASSOCIATEDCONSTANT, TYMETHOD, METHOD -> VALUES
        }
}

interface QualifiedNamedItemBase {
    val item: RsQualifiedNamedElement
    val itemName: String?
    val parentCrateRelativePath: String?
    val containingCrate: Crate?
}

class QualifiedNamedItem2(
    override val item: RsQualifiedNamedElement,
    /**
     * First segment is crate name (can be "crate").
     * Last segment is item name.
     */
    val path: Array<String>,
    /** corresponds to `path.first()` */
    override val containingCrate: Crate,
) : QualifiedNamedItemBase {
    override val itemName: String
        get() = path.last()
    override val parentCrateRelativePath: String
        get() = path.copyOfRange(1, path.size - 1).joinToString("::")

    override fun toString(): String = path.joinToString("::")
}
