/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.ide.utils.import.ImportCandidatesCollector
import org.rust.ide.utils.import.ImportContext
import org.rust.lang.core.psi.RsConstParameter
import org.rust.lang.core.psi.RsLifetimeParameter
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.consts.CtValue
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.stdext.withPrevious

private const val MAX_SHORT_TYPE_LEN = 50

fun Ty.render(
    context: RsElement? = null,
    level: Int = Int.MAX_VALUE,
    unknown: String = "<unknown>",
    anonymous: String = "<anonymous>",
    unknownLifetime: String = "'<unknown>",
    unknownConst: String = "<unknown>",
    integer: String = "{integer}",
    float: String = "{float}",
    useQualifiedName: Set<RsQualifiedNamedElement> = emptySet(),
    includeTypeArguments: Boolean = true,
    includeLifetimeArguments: Boolean = false,
    useAliasNames: Boolean = true,
    skipUnchangedDefaultTypeArguments: Boolean = true
): String = TypeRenderer(
    context = context,
    unknown = unknown,
    anonymous = anonymous,
    unknownLifetime = unknownLifetime,
    unknownConst = unknownConst,
    integer = integer,
    float = float,
    useQualifiedName = useQualifiedName,
    includeTypeArguments = includeTypeArguments,
    includeLifetimeArguments = includeLifetimeArguments,
    useAliasNames = useAliasNames,
    skipUnchangedDefaultTypeArguments = skipUnchangedDefaultTypeArguments
).render(this, level)

fun Ty.renderInsertionSafe(
    context: RsElement? = null,
    level: Int = Int.MAX_VALUE,
    useQualifiedName: Set<RsQualifiedNamedElement> = emptySet(),
    includeTypeArguments: Boolean = true,
    includeLifetimeArguments: Boolean = false,
    useAliasNames: Boolean = true,
    skipUnchangedDefaultTypeArguments: Boolean = true
): String = TypeRenderer(
    context = context,
    unknown = "_",
    anonymous = "_",
    unknownLifetime = "'_",
    unknownConst = "{}",
    integer = "_",
    float = "_",
    useQualifiedName = useQualifiedName,
    includeTypeArguments = includeTypeArguments,
    includeLifetimeArguments = includeLifetimeArguments,
    useAliasNames = useAliasNames,
    skipUnchangedDefaultTypeArguments = skipUnchangedDefaultTypeArguments
).render(this, level)

val Ty.shortPresentableText: String
    get() = generateSequence(1) { it + 1 }
        .map { render(level = it, unknown = "?") }
        .withPrevious()
        .takeWhile { (cur, prev) ->
            cur != prev && (prev == null || cur.length <= MAX_SHORT_TYPE_LEN)
        }.last().first

private data class TypeRenderer(
    val context: RsElement?,
    val unknown: String,
    val anonymous: String,
    val unknownLifetime: String,
    val unknownConst: String,
    val integer: String,
    val float: String,
    val useQualifiedName: Set<RsQualifiedNamedElement>,
    val includeTypeArguments: Boolean,
    val includeLifetimeArguments: Boolean,
    val useAliasNames: Boolean,
    val skipUnchangedDefaultTypeArguments: Boolean
) {
    fun render(ty: Ty, level: Int): String {
        require(level >= 0)

        if (ty == TyUnknown) return unknown

        if (level == 0) return "…"

        val render = { subTy: Ty ->
            render(subTy, level - 1)
        }

        val aliasedBy = ty.aliasedBy
        if (useAliasNames && aliasedBy != null) {
            return formatBoundElement(aliasedBy, render)
        }

        if (ty is TyPrimitive) {
            return when (ty) {
                is TyBool -> "bool"
                is TyChar -> "char"
                is TyUnit -> "()"
                is TyNever -> "!"
                is TyStr -> "str"
                is TyInteger -> ty.name
                is TyFloat -> ty.name
            }
        }

        return when (ty) {
            is TyFunction -> formatFnLike("fn", ty.paramTypes, ty.retType, render)
            is TySlice -> "[${render(ty.elementType)}]"

            is TyTuple -> if (ty.types.size == 1) {
                "(${render(ty.types.single())},)"
            } else {
                ty.types.joinToString(", ", "(", ")", transform = render)
            }
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
                append(ty.target.element.name)
                if (includeTypeArguments) append(formatProjectionGenerics(ty, render))
            }
            is TyTraitObject -> ty.traits.joinToString("+", "dyn ") { formatTrait(it, render) }
            is TyAnon -> ty.traits.joinToString("+", "impl ") { formatTrait(it, render) }
            is TyAdt -> buildString {
                append(getName(ty.item) ?: return anonymous)
                if (includeTypeArguments) append(formatAdtGenerics(ty, render))
            }
            is TyInfer -> when (ty) {
                is TyInfer.TyVar -> "_"
                is TyInfer.IntVar -> integer
                is TyInfer.FloatVar -> float
            }
            is TyPlaceholder -> "_"
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
            if (retType !is TyUnit) {
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
                ?: TyUnit.INSTANCE
            append(formatFnLike(name, paramTypes, retType, render))
        } else {
            append(name)
            if (includeTypeArguments) append(formatTraitGenerics(trait, render))
        }
    }

    private fun formatAdtGenerics(adt: TyAdt, render: (Ty) -> String): String {
        val visibleTypes = formatGenerics(adt.item, adt.typeParameterValues, render)
        return if (visibleTypes.isEmpty()) "" else visibleTypes.joinToString(", ", "<", ">")
    }

    private fun formatProjectionGenerics(projection: TyProjection, render: (Ty) -> String): String {
        val visibleTypes = formatGenerics(projection.target.element, projection.typeParameterValues, render)
        return if (visibleTypes.isEmpty()) "" else visibleTypes.joinToString(", ", "<", ">")
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
            append(getName(boundElement.element) ?: return anonymous)
            val visibleTypes = formatBoundElementGenerics(boundElement, render)
            append(if (visibleTypes.isEmpty()) "" else visibleTypes.joinToString(", ", "<", ">"))
        }
    }

    private fun formatBoundElementGenerics(
        boundElement: BoundElement<RsGenericDeclaration>,
        render: (Ty) -> String
    ): List<String> = formatGenerics(boundElement.element, boundElement.subst, render)

    private fun formatGenerics(
        declaration: RsGenericDeclaration,
        subst: Substitution,
        render: (Ty) -> String
    ): List<String> {
        val renderedList = mutableListOf<String>()
        var nonDefaultParamFound = false
        for (parameter in declaration.getGenericParameters().asReversed()) {
            if (skipUnchangedDefaultTypeArguments && !nonDefaultParamFound) {
                if (parameter is RsTypeParameter &&
                    parameter.typeReference != null &&
                    parameter.typeReference?.normType?.isEquivalentTo(subst[parameter]) == true) {
                    continue
                } else {
                    nonDefaultParamFound = true
                }
            }

            val rendered = when (parameter) {
                is RsLifetimeParameter -> if (includeLifetimeArguments) render(subst[parameter] ?: ReUnknown) else continue
                is RsTypeParameter -> render(subst[parameter] ?: TyUnknown)
                is RsConstParameter -> render(subst[parameter] ?: CtUnknown, wrapParameterInBraces = true)
                else -> error("unreachable")
            }
            renderedList.add(rendered)
        }
        renderedList.reverse()
        return renderedList
    }

    private fun getName(element: RsNamedElement): String? =
        if (element is RsQualifiedNamedElement && element in useQualifiedName) {
            val candidate = run {
                if (context == null) return@run null
                val importContext = ImportContext.from(context, ImportContext.Type.OTHER) ?: return@run null
                ImportCandidatesCollector.findImportCandidate(importContext, element)
            }
            candidate?.info?.usePath ?: element.qualifiedName
        } else {
            element.name
        }
}
