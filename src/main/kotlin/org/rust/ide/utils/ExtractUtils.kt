/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.addIfNotNull
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.hasCtConstParameters
import org.rust.lang.core.types.infer.hasTyTypeParameters
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type

/**
 * This file contains tools for extracting a subset of related type/const parameters, lifetimes and their constraints
 * from a given set of types.
 */


/**
 * Holds type parameters, lifetimes, const parameters and where clauses.
 * It serves as a combination of several `RsGenericDeclaration`s.
 *
 * Can be filtered by a set of types/type references to only returns parameters/constraints that are needed by these
 * given types/type references.
 */
data class GenericConstraints(
    val lifetimes: List<RsLifetimeParameter> = emptyList(),
    val typeParameters: List<RsTypeParameter> = emptyList(),
    val constParameters: List<RsConstParameter> = emptyList(),
    val whereClauses: List<RsWhereClause> = emptyList()
) {
    fun filterByTypeReferences(references: List<RsTypeReference>): GenericConstraints {
        val types = references.map { it.type }
        val typeParameters = gatherTypeParameters(types, typeParameters)
        val lifetimes = gatherLifetimesFromTypeReferences(references, lifetimes, typeParameters)
        val constParameters = constParameters.filter { param -> types.any { matchesConstParameter(it, param) } }
        return GenericConstraints(lifetimes, typeParameters, constParameters, whereClauses)
    }

    fun filterByTypes(types: List<Ty>): GenericConstraints {
        val typeParameters = gatherTypeParameters(types, typeParameters)
        val constParameters = constParameters
            .filter { param -> types.any { matchesConstParameter(it, param) } }
        return GenericConstraints(gatherLifetimesFromTypeParameters(lifetimes, typeParameters),
            typeParameters, constParameters, whereClauses)
    }

    fun buildTypeParameters(): String {
        val all: List<RsNameIdentifierOwner> = lifetimes + typeParameters + constParameters
        return if (all.isNotEmpty()) {
            all.joinToString(", ", prefix = "<", postfix = ">") { it.text }
        } else {
            ""
        }
    }

    fun buildWhereClause(): String {
        val wherePredList = whereClauses.flatMap { it.wherePredList }

        val parameterMap = typeParameters.filter { it.name != null }.associateBy { it.name!! }
        val lifetimeMap = lifetimes.filter { it.name != null }.associateBy { it.name!! }
        val parameterToBounds = mutableMapOf<String, MutableSet<String>>()
        val lifetimeToBounds = mutableMapOf<String, MutableSet<String>>()

        fun normalizePredicate(text: String, name: String): String = text.removePrefix("$name:").trim()
        fun addIfMissing(map: MutableMap<String, MutableSet<String>>, key: String) {
            if (key !in map) {
                map[key] = mutableSetOf()
            }
        }

        loop@ for (predicate in wherePredList) {
            val typeRef = predicate.typeReference
            val lifetime = predicate.lifetime
            when {
                // type bound
                typeRef != null && hasTypeParameter(typeRef, parameterMap) -> {
                    val parameterText = (predicate.forLifetimes?.text?.plus(" ") ?: "") + typeRef.text
                    addIfMissing(parameterToBounds, parameterText)
                    predicate.typeParamBounds?.polyboundList?.forEach {
                        parameterToBounds[parameterText]?.add(it.text)
                    }
                }
                // lifetime bound
                lifetime != null -> {
                    val lifetimePredicate = createLifetimePredicate(
                        predicate,
                        lifetime,
                        predicate.lifetimeParamBounds,
                        lifetimeMap
                    ) ?: continue@loop
                    val name = lifetime.name ?: continue@loop
                    addIfMissing(lifetimeToBounds, name)
                    lifetimeToBounds[name]?.add(normalizePredicate(lifetimePredicate, name))
                }
            }
        }

        fun mapBounds(map: Map<String, Set<String>>, key: String): String? {
            val bounds = map[key] ?: return null
            if (bounds.isEmpty()) return null
            return "$key: ${bounds.sorted().joinToString(" + ")}"
        }

        val bounds = lifetimeToBounds.keys.sorted().mapNotNull { mapBounds(lifetimeToBounds, it) } +
            parameterToBounds.keys.sorted().mapNotNull { mapBounds(parameterToBounds, it) }

        return if (bounds.isNotEmpty()) {
            bounds.joinToString(separator = ",", prefix = " where ")
        } else {
            ""
        }
    }

    fun withoutTypes(params: List<RsTypeParameter>): GenericConstraints {
        val types = typeParameters - params
        return copy(typeParameters = types)
    }

    companion object {
        /**
         * Recursively finds parent `RsGenericDeclarations` and collects all of their type parameters and
         * their constraints.
         */
        fun create(context: PsiElement): GenericConstraints {
            val typeParameters = mutableSetOf<RsTypeParameter>()
            val lifetimes = mutableSetOf<RsLifetimeParameter>()
            val constParameters = mutableSetOf<RsConstParameter>()
            val whereClauses = mutableListOf<RsWhereClause>()

            var genericDecl: RsGenericDeclaration? = (context as? RsGenericDeclaration) ?: context.parentOfType()
            while (genericDecl != null) {
                typeParameters += genericDecl.typeParameters
                lifetimes += genericDecl.lifetimeParameters
                constParameters += genericDecl.constParameters
                whereClauses.addIfNotNull(genericDecl.whereClause)

                if (genericDecl is RsAbstractable && genericDecl.owner.javaClass !in TRANSITIVE_GENERIC_OWNERS) break

                genericDecl = genericDecl.parentOfType()
            }

            return GenericConstraints(lifetimes.toList(), typeParameters.toList(), constParameters.toList(), whereClauses)
        }

        private val TRANSITIVE_GENERIC_OWNERS = listOf(
            RsAbstractableOwner.Impl::class.java,
            RsAbstractableOwner.Trait::class.java
        )
    }
}

private fun gatherTypeParameters(
    types: List<Ty>,
    parameters: List<RsTypeParameter>
): List<RsTypeParameter> {
    val parameterMap = parameters.filter { it.name != null }.associateBy { it.name!! }
    val collected = mutableSetOf<RsTypeParameter>()
    val visitor = CollectTypeParametersTypeVisitor(parameterMap, collected)
    for (type in types) {
        type.visitWith(visitor)
    }
    return collected.sortedBy { parameters.indexOf(it) }
}

private data class CollectLifetimesVisitor(
    val parameters: Map<String, RsTypeParameter>,
    val lifetimeMap: Map<String, RsLifetimeParameter>,
    val collected: MutableSet<RsLifetimeParameter>
) : RsRecursiveVisitor() {

    override fun visitTypeReference(ref: RsTypeReference) {
        super.visitTypeReference(ref)
        val type = ref.type as? TyTypeParameter ?: return
        val parameter = parameters[type.name] ?: return
        parameter.bounds.forEach { bound ->
            bound.accept(this)
        }
    }

    override fun visitLifetime(lifetime: RsLifetime) {
        super.visitLifetime(lifetime)
        val parameter = lifetimeMap[lifetime.name] ?: return
        if (parameter !in collected) {
            collected.add(parameter)
            parameter.accept(this)
        }
    }
}

private fun gatherLifetimesFromTypeReferences(
    references: List<RsTypeReference>,
    lifetimes: List<RsLifetimeParameter>,
    parameters: List<RsTypeParameter>
): List<RsLifetimeParameter> = gatherLifetimes(lifetimes, parameters, references)

private fun gatherLifetimesFromTypeParameters(
    lifetimes: List<RsLifetimeParameter>,
    parameters: List<RsTypeParameter>
): List<RsLifetimeParameter> = gatherLifetimes(lifetimes, parameters, parameters)

private fun gatherLifetimes(
    lifetimes: List<RsLifetimeParameter>,
    parameters: List<RsTypeParameter>,
    elements: List<PsiElement>
): List<RsLifetimeParameter> {
    val parameterMap = parameters.filter { it.name != null }.associateBy { it.name!! }
    val lifetimeMap = lifetimes.filter { it.name != null }.associateBy { it.name!! }
    val collected = mutableSetOf<RsLifetimeParameter>()

    for (element in elements) {
        element.accept(CollectLifetimesVisitor(parameterMap, lifetimeMap, collected))
    }

    return collected.sortedBy { lifetimes.indexOf(it) }
}

private data class CollectTypeParametersTypeVisitor(
    val parameters: Map<String, RsTypeParameter>,
    val collected: MutableSet<RsTypeParameter>
) : TypeVisitor {
    override fun visitTy(ty: Ty): Boolean {
        return when {
            ty is TyTypeParameter -> {
                val type = ty as? TyTypeParameter ?: return true
                val parameter = parameters[type.name] ?: return true
                collected.add(parameter)

                parameter.bounds.forEach { bound ->
                    bound.accept(CollectTypeParametersVisitor(this))
                }
                return true
            }
            ty.hasTyTypeParameters -> ty.superVisitWith(this)
            else -> super.visitTy(ty)
        }
    }
}

private data class CollectTypeParametersVisitor(val typeVisitor: CollectTypeParametersTypeVisitor) : RsRecursiveVisitor() {
    override fun visitTypeReference(ref: RsTypeReference) {
        ref.type.visitWith(typeVisitor)
        super.visitTypeReference(ref)
    }
}

private data class HasConstParameterVisitor(val parameter: RsConstParameter) : TypeVisitor {

    override fun visitTy(ty: Ty): Boolean =
        if (ty.hasCtConstParameters) ty.superVisitWith(this) else false

    override fun visitConst(const: Const): Boolean =
        when {
            const is CtConstParameter -> const.parameter == parameter
            const.hasCtConstParameters -> const.superVisitWith(this)
            else -> false
        }
}

private data class HasTypeParameterVisitor(
    val parameters: Map<String, RsTypeParameter>
) : TypeVisitor {
    override fun visitTy(ty: Ty): Boolean =
        when {
            ty is TyTypeParameter -> ty.name in parameters
            ty.hasTyTypeParameters -> ty.superVisitWith(this)
            else -> false
        }
}

private fun matchesConstParameter(type: Ty, parameter: RsConstParameter): Boolean =
    type.visitWith(HasConstParameterVisitor(parameter))

private fun hasTypeParameter(ref: RsTypeReference, map: Map<String, RsTypeParameter>): Boolean =
    HasTypeParameterVisitor(map).visitTy(ref.type)

/**
 * Create a predicate if the lifetime is in the map and at least one of its bounds is in the map.
 * Bounds that are not in the map are removed.
 */
private fun createLifetimePredicate(
    predicate: RsWherePred,
    lifetime: RsLifetime,
    lifetimeParamBounds: RsLifetimeParamBounds?,
    lifetimeMap: Map<String, RsLifetimeParameter>
): String? {
    if (lifetime.name !in lifetimeMap) return null
    if (lifetimeParamBounds == null) return predicate.text
    val bounds = lifetimeParamBounds.lifetimeList.filter { it.name in lifetimeMap }
    return if (bounds.isNotEmpty()) {
        "${lifetime.text}: ${bounds.joinToString(" + ") { it.text }}"
    } else {
        null
    }
}
