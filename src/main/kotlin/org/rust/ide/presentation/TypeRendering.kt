/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.jetbrains.annotations.TestOnly
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.lifetimeParameters
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.stdext.withPrevious

private const val MAX_SHORT_TYPE_LEN = 50

val Ty.shortPresentableText: String
    get() = generateSequence(1) { it + 1 }
        .map { TypeRenderer.SHORT_WITH_ALIASES.render(this, level = it) }
        .withPrevious()
        .takeWhile { (cur, prev) ->
            cur != prev && (prev == null || cur.length <= MAX_SHORT_TYPE_LEN)
        }.last().first

val Ty.insertionSafeText: String
    get() = TypeRenderer.INSERTION_SAFE.render(this)

val Ty.insertionSafeTextWithLifetimes: String
    get() = TypeRenderer.INSERTION_SAFE_WITH_LIFETIMES.render(this)

val Ty.textWithAliasNames: String
    @TestOnly get() = TypeRenderer.WITH_ALIASES.render(this)

fun tyToString(ty: Ty): String = TypeRenderer.DEFAULT.render(ty)

fun tyToStringWithoutTypeArgs(ty: Ty): String = TypeRenderer.DEFAULT_WITHOUT_TYPE_ARGUMENTS.render(ty)

private data class TypeRenderer(
    val unknown: String = "<unknown>",
    val anonymous: String = "<anonymous>",
    val unknownLifetime: String = "'<unknown>",
    val integer: String = "{integer}",
    val float: String = "{float}",
    val includeTypeArguments: Boolean = true,
    val includeLifetimeArguments: Boolean = false,
    val useAliasNames: Boolean = false
) {
    fun render(ty: Ty): String = render(ty, Int.MAX_VALUE)

    fun render(ty: Ty, level: Int): String {
        require(level >= 0)

        if (ty == TyUnknown) return unknown
        if (ty is TyPrimitive) {
            return when (ty) {
                is TyBool -> "bool"
                is TyChar -> "char"
                is TyUnit -> "()"
                is TyNever -> "!"
                is TyStr -> "str"
                is TyInteger -> ty.name
                is TyFloat -> ty.name
                else -> error("unreachable")
            }
        }

        if (level == 0) return "…"

        val render = { subTy: Ty ->
            render(subTy, level - 1)
        }

        return when (ty) {
            is TyFunction -> buildString {
                ty.paramTypes.joinTo(this, ", ", "fn(", ")", transform = render)
                if (ty.retType != TyUnit) {
                    append(" -> ")
                    append(render(ty.retType))
                }
            }
            is TySlice -> "[${render(ty.elementType)}]"

            is TyTuple -> ty.types.joinToString(", ", "(", ")", transform = render)
            is TyArray -> "[${render(ty.base)}; ${ty.size ?: unknown}]"
            is TyReference -> buildString {
                append('&')
                if (includeLifetimeArguments && (ty.region is ReEarlyBound || ty.region is ReStatic)) {
                    append(render(ty.region))
                    append(" ")
                }
                if (ty.mutability.isMut) append("mut ")
                append(render(ty.referenced, level))
            }
            is TyPointer -> buildString {
                append("*")
                append(if (ty.mutability.isMut) "mut" else "const")
                append(" ")
                append(render(ty.referenced))
            }
            is TyTypeParameter -> ty.name ?: anonymous
            is TyProjection -> buildString {
                val traitName = ty.trait.element.name ?: return anonymous
                if (ty.type.isSelf) {
                    append("Self::")
                } else {
                    append("<")
                    append(ty.type)
                    append(" as ")
                    append(traitName)
                    if (includeTypeArguments) append(formatTraitGenerics(ty.trait, render, false))
                    append(">::")
                }
                append(ty.target.name)
            }
            is TyTraitObject -> formatTrait(ty.trait, render)
            is TyAnon -> ty.traits.joinToString("+", "impl ") { formatTrait(it, render) }
            is TyAdt -> buildString {
                if (useAliasNames && ty.aliasedBy != null) {
                    append(formatBoundElement(ty.aliasedBy, render))
                } else {
                    append(ty.item.name ?: return anonymous)
                    if (includeTypeArguments) append(formatGenerics(ty, render))
                }
            }
            is TyInfer -> when (ty) {
                is TyInfer.TyVar -> "_"
                is TyInfer.IntVar -> integer
                is TyInfer.FloatVar -> float
            }
            is FreshTyInfer -> "<fresh>" // really should never be displayed; debug only
            else -> error("unreachable")
        }
    }

    private fun render(region: Region): String =
        if (region == ReUnknown) unknownLifetime else region.toString()

    private fun formatTrait(trait: BoundElement<RsTraitItem>, render: (Ty) -> String): String = buildString {
        append(trait.element.name ?: return anonymous)
        if (includeTypeArguments) append(formatTraitGenerics(trait, render))
    }

    private fun formatGenerics(adt: TyAdt, render: (Ty) -> String): String {
        val typeArgumentNames = adt.typeArguments.map(render)
        val regionArgumentNames = if (includeLifetimeArguments) {
            adt.regionArguments.map { render(it) }
        } else {
            emptyList()
        }
        val generics = regionArgumentNames + typeArgumentNames
        return if (generics.isEmpty()) "" else generics.joinToString(", ", "<", ">")
    }

    private fun formatTraitGenerics(
        trait: BoundElement<RsTraitItem>,
        render: (Ty) -> String,
        includeAssoc: Boolean = true
    ): String {
        val assoc = if (includeAssoc) {
            trait.element.associatedTypesTransitively.mapNotNull {
                val name = it.name ?: return@mapNotNull null
                name + "=" + render(trait.assoc[it] ?: TyUnknown)
            }
        } else {
            emptyList()
        }
        val visibleTypes = formatBoundElementGenerics(trait, render) + assoc
        return if (visibleTypes.isEmpty()) "" else visibleTypes.joinToString(", ", "<", ">")
    }

    private fun <T> formatBoundElement(
        boundElement: BoundElement<T>,
        render: (Ty) -> String
    ): String
        where T : RsGenericDeclaration,
              T : RsNamedElement {
        return buildString {
            append(boundElement.element.name ?: return anonymous)
            val visibleTypes = formatBoundElementGenerics(boundElement, render)
            append(if (visibleTypes.isEmpty()) "" else visibleTypes.joinToString(", ", "<", ">"))
        }
    }

    private fun formatBoundElementGenerics(
        boundElement: BoundElement<RsGenericDeclaration>,
        render: (Ty) -> String
    ): List<String> {
        val tySubst = boundElement.element.typeParameters.map { render(boundElement.subst[it] ?: TyUnknown) }
        val regionSubst = if (includeLifetimeArguments) {
            boundElement.element.lifetimeParameters.map { render(boundElement.subst[it] ?: ReUnknown) }
        } else {
            emptyList()
        }
        return regionSubst + tySubst
    }

    companion object {
        val DEFAULT: TypeRenderer = TypeRenderer()
        val SHORT_WITH_ALIASES: TypeRenderer = TypeRenderer(unknown = "?", useAliasNames = true)
        val DEFAULT_WITHOUT_TYPE_ARGUMENTS: TypeRenderer = TypeRenderer(includeTypeArguments = false)
        val INSERTION_SAFE: TypeRenderer = TypeRenderer(
            unknown = "_",
            anonymous = "_",
            unknownLifetime = "'_",
            integer = "_",
            float = "_"
        )
        val INSERTION_SAFE_WITH_LIFETIMES: TypeRenderer = INSERTION_SAFE.copy(includeLifetimeArguments = true)
        val WITH_ALIASES: TypeRenderer = TypeRenderer(useAliasNames = true)
    }
}
