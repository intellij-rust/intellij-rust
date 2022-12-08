/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import org.rust.ide.presentation.shortPresentableText
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Kind
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtValue
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.*

@Suppress("UnstableApiUsage")
class RsTypeHintsPresentationFactory(
    private val factory: PresentationFactory,
    private val showObviousTypes: Boolean
) {
    fun typeHint(type: Ty): InlayPresentation = factory.roundWithBackground(
        listOf(text(": "), hint(type, 1)).join()
    )

    private fun hint(kind: Kind, level: Int): InlayPresentation {
        if (kind is Ty) {
            val alias = kind.aliasedBy
            if (alias != null) {
                return aliasTypeHint(alias, level)
            }
        }

        return when (kind) {
            is TyTuple -> tupleTypeHint(kind, level)
            is TyAdt -> adtTypeHint(kind, level)
            is TyFunction -> functionTypeHint(kind, level)
            is TyReference -> referenceTypeHint(kind, level)
            is TyPointer -> pointerTypeHint(kind, level)
            is TyProjection -> projectionTypeHint(kind, level)
            is TyTypeParameter -> typeParameterTypeHint(kind)
            is TyArray -> arrayTypeHint(kind, level)
            is TySlice -> sliceTypeHint(kind, level)
            is TyTraitObject -> traitObjectTypeHint(kind, level)
            is TyAnon -> anonTypeHint(kind, level)
            is CtConstParameter -> constParameterTypeHint(kind)
            is CtValue -> text(kind.expr.toString())
            is Ty -> text(kind.shortPresentableText)
            else -> text(null)
        }
    }

    private fun functionTypeHint(type: TyFunction, level: Int): InlayPresentation {
        val parameters = type.paramTypes
        val returnType = type.retType

        val startWithPlaceholder = checkSize(level, parameters.size + 1)
        val fn = if (parameters.isEmpty()) {
            text("fn()")
        } else {
            factory.collapsible(
                prefix = text("fn("),
                collapsed = text(PLACEHOLDER),
                expanded = { parametersHint(parameters, level + 1) },
                suffix = text(")"),
                startWithPlaceholder = startWithPlaceholder
            )
        }

        if (returnType !is TyUnit) {
            val ret = factory.collapsible(
                prefix = text(" → "),
                collapsed = text(PLACEHOLDER),
                expanded = { hint(returnType, level + 1) },
                suffix = text(""),
                startWithPlaceholder = startWithPlaceholder
            )
            return factory.seq(fn, ret)
        }

        return fn
    }

    private fun tupleTypeHint(type: TyTuple, level: Int): InlayPresentation =
        factory.collapsible(
            prefix = text("("),
            collapsed = text(PLACEHOLDER),
            expanded = { tupleTypesHint(type.types, level + 1) },
            suffix = text(")"),
            startWithPlaceholder = checkSize(level, type.types.size)
        )

    private fun tupleTypesHint(types: List<Ty>, level: Int): InlayPresentation = if (types.size == 1) {
        factory.seq(hint(types.single(), level), text(","))
    } else {
        types.map { hint(it, level) }.join(", ")
    }

    private fun adtTypeHint(type: TyAdt, level: Int): InlayPresentation {
        val adtName = type.item.name
        val typeDeclaration = type.item
        val typeNamePresentation = factory.psiSingleReference(text(adtName)) { typeDeclaration }
        val typeArguments = type.typeArguments.zip(type.item.typeParameters)

        return withGenericsTypeHint(typeNamePresentation, typeArguments, type.constArguments, level)
    }

    private fun aliasTypeHint(boundElement: BoundElement<RsTypeAlias>, level: Int): InlayPresentation {
        val alias = boundElement.element

        val adtName = alias.name
        val typeNamePresentation = factory.psiSingleReference(text(adtName)) { alias }
        val typeArguments = alias.typeParameters.map { (boundElement.subst[it] ?: TyUnknown) to it }

        return withGenericsTypeHint(typeNamePresentation, typeArguments, emptyList(), level)
    }

    private fun projectionTypeHint(type: TyProjection, level: Int): InlayPresentation {
        val collapsible = factory.collapsible(
            prefix = text("<"),
            collapsed = text(PLACEHOLDER),
            expanded = {
                val typePresentation = hint(type.type, level + 1)
                val traitPresentation = traitItemTypeHint(type.trait, level + 1, false)
                listOf(typePresentation, traitPresentation).join(" as ")
            },
            suffix = text(">"),
            startWithPlaceholder = checkSize(level, 2)
        )

        val target = type.target.element
        val targetName = target.name
        val targetPresentation = factory.psiSingleReference(text(targetName)) { target }
        val typeNamePresentation = listOf(collapsible, targetPresentation).join("::")
        val typeArguments = type.typeArguments.zip(target.typeParameters)
        return withGenericsTypeHint(typeNamePresentation, typeArguments, emptyList(), level)
    }

    private fun withGenericsTypeHint(
        typeNamePresentation: InlayPresentation,
        typeArguments: List<Pair<Ty, RsTypeParameter>>,
        constArguments: List<Const>,
        level: Int
    ): InlayPresentation {
        val userVisibleKindArguments = mutableListOf<Kind>()
        for ((argument, parameter) in typeArguments) {
            if (!showObviousTypes && isDefaultTypeParameter(argument, parameter)) {
                // don't show default types
                continue
            }
            userVisibleKindArguments.add(argument)
        }
        for (argument in constArguments) {
            userVisibleKindArguments.add(argument)
        }

        if (userVisibleKindArguments.isNotEmpty()) {
            val collapsible = factory.collapsible(
                prefix = text("<"),
                collapsed = text(PLACEHOLDER),
                expanded = { parametersHint(userVisibleKindArguments, level + 1) },
                suffix = text(">"),
                startWithPlaceholder = checkSize(level, userVisibleKindArguments.size)
            )
            return listOf(typeNamePresentation, collapsible).join()
        }
        return typeNamePresentation
    }

    private fun referenceTypeHint(type: TyReference, level: Int): InlayPresentation = listOf(
        text("&" + if (type.mutability.isMut) "mut " else ""),
        hint(type.referenced, level) // level is not incremented intentionally
    ).join()

    private fun pointerTypeHint(type: TyPointer, level: Int): InlayPresentation = listOf(
        text("*" + if (type.mutability.isMut) "mut " else "const "),
        hint(type.referenced, level) // level is not incremented intentionally
    ).join()

    private fun typeParameterTypeHint(type: TyTypeParameter): InlayPresentation {
        val parameter = type.parameter
        if (parameter is TyTypeParameter.Named) {
            return factory.psiSingleReference(text(parameter.name)) { parameter.parameter }
        }
        return text(parameter.name)
    }

    private fun constParameterTypeHint(const: CtConstParameter): InlayPresentation =
        factory.psiSingleReference(text(const.parameter.name)) { const.parameter }

    private fun arrayTypeHint(type: TyArray, level: Int): InlayPresentation =
        factory.collapsible(
            prefix = text("["),
            collapsed = text(PLACEHOLDER),
            expanded = {
                val basePresentation = hint(type.base, level + 1)
                val sizePresentation = text(type.size?.toString())
                listOf(basePresentation, sizePresentation).join("; ")
            },
            suffix = text("]"),
            startWithPlaceholder = checkSize(level, 1)
        )

    private fun sliceTypeHint(type: TySlice, level: Int): InlayPresentation =
        factory.collapsible(
            prefix = text("["),
            collapsed = text(PLACEHOLDER),
            expanded = { hint(type.elementType, level + 1) },
            suffix = text("]"),
            startWithPlaceholder = checkSize(level, 1)
        )

    private fun traitObjectTypeHint(type: TyTraitObject, level: Int): InlayPresentation =
        factory.collapsible(
            prefix = text("dyn "),
            collapsed = text(PLACEHOLDER),
            expanded = { type.traits.map { traitItemTypeHint(it, level + 1, true) }.join("+") },
            suffix = text(""),
            startWithPlaceholder = checkSize(level, 1)
        )

    private fun anonTypeHint(type: TyAnon, level: Int): InlayPresentation =
        factory.collapsible(
            prefix = text("impl "),
            collapsed = text(PLACEHOLDER),
            expanded = { type.traits.map { traitItemTypeHint(it, level + 1, true) }.join("+") },
            suffix = text(""),
            startWithPlaceholder = checkSize(level, type.traits.size)
        )

    private fun parametersHint(kinds: List<Kind>, level: Int): InlayPresentation =
        kinds.map { hint(it, level) }.join(", ")

    private fun traitItemTypeHint(
        trait: BoundElement<RsTraitItem>,
        level: Int,
        includeAssoc: Boolean
    ): InlayPresentation {
        val traitPresentation = factory.psiSingleReference(text(trait.element.name)) { trait.element }

        val typeParametersPresentations = mutableListOf<InlayPresentation>()
        for (parameter in trait.element.typeParameters) {
            val argument = trait.subst[parameter] ?: continue
            if (!showObviousTypes && isDefaultTypeParameter(argument, parameter)) {
                // don't show default types
                continue
            }
            val parameterPresentation = hint(argument, level + 1)
            typeParametersPresentations.add(parameterPresentation)
        }

        val assocTypesPresentations = mutableListOf<InlayPresentation>()
        if (includeAssoc) {
            for (alias in trait.element.associatedTypesTransitively) {
                val aliasName = alias.name ?: continue
                val type = trait.assoc[alias] ?: continue
                if (!showObviousTypes && isDefaultTypeAlias(type, alias)) {
                    // don't show default types
                    continue
                }

                val aliasPresentation = factory.psiSingleReference(text(aliasName)) { alias }

                val presentation = listOf(aliasPresentation, text("="), hint(type, level + 1)).join()
                assocTypesPresentations.add(presentation)
            }
        }

        val innerPresentations = typeParametersPresentations + assocTypesPresentations

        return if (innerPresentations.isEmpty()) {
            traitPresentation
        } else {
            val expanded = innerPresentations.join(", ")
            val traitTypesPresentation = factory.collapsible(
                prefix = text("<"),
                collapsed = text(PLACEHOLDER),
                expanded = { expanded },
                suffix = text(">"),
                startWithPlaceholder = checkSize(level, innerPresentations.size)
            )
            listOf(traitPresentation, traitTypesPresentation).join()
        }
    }

    private fun checkSize(level: Int, elementsCount: Int): Boolean =
        level + elementsCount > FOLDING_THRESHOLD

    private fun isDefaultTypeParameter(argument: Ty, parameter: RsTypeParameter): Boolean =
        argument.isEquivalentTo(parameter.typeReference?.normType)

    private fun isDefaultTypeAlias(argument: Ty, alias: RsTypeAlias): Boolean =
        argument.isEquivalentTo(alias.typeReference?.normType)

    private fun List<InlayPresentation>.join(separator: String = ""): InlayPresentation {
        if (separator.isEmpty()) {
            return factory.seq(*toTypedArray())
        }
        val presentations = mutableListOf<InlayPresentation>()
        var first = true
        for (presentation in this) {
            if (!first) {
                presentations.add(text(separator))
            }
            presentations.add(presentation)
            first = false
        }
        return factory.seq(*presentations.toTypedArray())
    }

    private fun text(text: String?): InlayPresentation = factory.smallText(text ?: "?")

    companion object {
        private const val PLACEHOLDER: String = "…"
        private const val FOLDING_THRESHOLD: Int = 3
    }
}
