/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.consts.CtValue
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.stdext.withPrevious

private const val MAX_SHORT_TYPE_LEN = 50

fun Ty.render(
    level: Int = Int.MAX_VALUE,
    unknown: String = "<unknown>",
    anonymous: String = "<anonymous>",
    unknownLifetime: String = "'<unknown>",
    unknownConst: String = "<unknown>",
    integer: String = "{integer}",
    float: String = "{float}",
    includeTypeArguments: Boolean = true,
    includeLifetimeArguments: Boolean = false,
    useAliasNames: Boolean = false,
    skipUnchangedDefaultTypeArguments: Boolean = false
): String = TypeRenderer(
    unknown = unknown,
    anonymous = anonymous,
    unknownLifetime = unknownLifetime,
    unknownConst = unknownConst,
    integer = integer,
    float = float,
    includeTypeArguments = includeTypeArguments,
    includeLifetimeArguments = includeLifetimeArguments,
    useAliasNames = useAliasNames,
    skipUnchangedDefaultTypeArguments = skipUnchangedDefaultTypeArguments
).render(this, level)

fun Ty.renderInsertionSafe(
    level: Int = Int.MAX_VALUE,
    includeTypeArguments: Boolean = true,
    includeLifetimeArguments: Boolean = false,
    useAliasNames: Boolean = false,
    skipUnchangedDefaultTypeArguments: Boolean = false
): String = TypeRenderer(
    unknown = "_",
    anonymous = "_",
    unknownLifetime = "'_",
    unknownConst = "{}",
    integer = "_",
    float = "_",
    includeTypeArguments = includeTypeArguments,
    includeLifetimeArguments = includeLifetimeArguments,
    useAliasNames = useAliasNames,
    skipUnchangedDefaultTypeArguments = skipUnchangedDefaultTypeArguments
).render(this, level)

val Ty.shortPresentableText: String
    get() = generateSequence(1) { it + 1 }
        .map { render(level = it, unknown = "?", useAliasNames = true) }
        .withPrevious()
        .takeWhile { (cur, prev) ->
            cur != prev && (prev == null || cur.length <= MAX_SHORT_TYPE_LEN)
        }.last().first

private data class TypeRenderer(
    val unknown: String,
    val anonymous: String,
    val unknownLifetime: String,
    val unknownConst: String,
    val integer: String,
    val float: String,
    val includeTypeArguments: Boolean,
    val includeLifetimeArguments: Boolean,
    val useAliasNames: Boolean,
    val skipUnchangedDefaultTypeArguments: Boolean
) {
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
            is TyFunction -> formatFnLike("fn", ty.paramTypes, ty.retType, render)
            is TySlice -> "[${render(ty.elementType)}]"

            is TyTuple -> ty.types.joinToString(", ", "(", ")", transform = render)
            is TyArray -> "[${render(ty.base)}; ${render(ty.const)}]"
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
            is TyTraitObject -> ty.traits.joinToString("+", "dyn ") { formatTrait(it, render) }
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

    private fun render(const: Const, wrapParameterInBraces: Boolean = false): String =
        when (const) {
            is CtValue -> const.toString()
            is CtConstParameter -> if (wrapParameterInBraces) "{ $const }" else const.toString()
            else -> unknownConst
        }

    private fun formatFnLike(fnType: String, paramTypes: List<Ty>, retType: Ty, render: (Ty) -> String): String =
        buildString {
            paramTypes.joinTo(this, ", ", "$fnType(", ")", transform = render)
            if (retType != TyUnit) {
                append(" -> ")
                append(render(retType))
            }
        }

    private fun formatTrait(trait: BoundElement<RsTraitItem>, render: (Ty) -> String): String = buildString {
        val name = trait.element.name ?: return anonymous
        if (trait.element.langAttribute in listOf("fn", "fn_once", "fn_mut")) {
            val paramTypes = trait.element.typeParameters
                .singleOrNull()
                ?.let { trait.subst[it] as? TyTuple }
                ?.types
                ?: return unknown
            val retType = trait.assoc.entries
                .find { it.key.name == "Output" }
                ?.value
                ?: TyUnit
            append(formatFnLike(name, paramTypes, retType, render))
        } else {
            append(name)
            if (includeTypeArguments) append(formatTraitGenerics(trait, render))
        }
    }

    private fun formatGenerics(adt: TyAdt, render: (Ty) -> String): String {
        val typeArguments = adt.typeArguments

        val typeArgumentNames = if (skipUnchangedDefaultTypeArguments) {
            adt.item.typeParameters.zip(typeArguments).filter { (param, argument) ->
                param.typeReference == null || param.typeReference?.type != argument
            }.map { (_, argument) -> render(argument) }
        } else {
            typeArguments.map(render)
        }

        val regionArgumentNames = if (includeLifetimeArguments) adt.regionArguments.map { render(it) } else emptyList()
        val constArgumentNames = adt.constArguments.map { render(it, wrapParameterInBraces = true) }
        val generics = regionArgumentNames + typeArgumentNames + constArgumentNames
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
        val constSubst = boundElement.element.constParameters.map { render(boundElement.subst[it] ?: CtUnknown) }
        return regionSubst + tySubst + constSubst
    }
}
