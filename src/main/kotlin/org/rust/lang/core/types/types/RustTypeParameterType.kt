package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.hierarchyContains
import org.rust.lang.core.psi.ext.resolveToTrait
import org.rust.lang.core.psi.ext.superTraits
import org.rust.lang.core.types.RustType

data class RustTypeParameterType private constructor(
    private val parameter: TypeParameter
) : RustType {

    constructor(parameter: RsTypeParameter) : this(Named(parameter))

    constructor(trait: RsTraitItem) : this(Self(trait))

    override fun getTraitsImplementedIn(project: Project): Sequence<RsTraitItem> =
        transitiveClosure(parameter.bounds)

    override fun getMethodsIn(project: Project): Sequence<RsFunction> =
        getTraitsImplementedIn(project).flatMap { it.functionList.asSequence() }

    override fun canUnifyWith(other: RustType, project: Project): Boolean {
        return this == other || parameter.bounds.all { canUnifyTraitBoundWith(it, other, project) }
    }

    override fun substitute(map: Map<RustTypeParameterType, RustType>): RustType = map[this] ?: this

    override fun toString(): String = parameter.name ?: "<unknown>"

    private interface TypeParameter {
        val name: String?
        val bounds: Sequence<RsTraitItem>
    }

    private data class Named(val parameter: RsTypeParameter) : TypeParameter {
        override val name: String? get() = parameter.name

        override val bounds: Sequence<RsTraitItem> get() {
            val owner = parameter.parent?.parent as? RsGenericDeclaration
            val whereBounds =
                owner?.whereClause?.wherePredList.orEmpty()
                    .asSequence()
                    .filter { (it.typeReference as? RsBaseType)?.path?.reference?.resolve() == parameter }
                    .flatMap { it.typeParamBounds?.polyboundList.orEmpty().asSequence() }

            return (parameter.typeParamBounds?.polyboundList.orEmpty().asSequence() + whereBounds)
                .mapNotNull { it.bound.traitRef?.resolveToTrait }
        }
    }

    private data class Self(val trait: RsTraitItem) : TypeParameter {
        override val name: String? get() = "Self"

        override val bounds: Sequence<RsTraitItem> get() = sequenceOf(trait)
    }
}

private fun transitiveClosure(traits: Sequence<RsTraitItem>): Sequence<RsTraitItem> {
    val result = mutableSetOf<RsTraitItem>()
    fun dfs(trait: RsTraitItem) {
        if (trait in result) return
        result += trait
        trait.superTraits.forEach(::dfs)
    }
    traits.forEach(::dfs)

    return result.asSequence()
}

private fun canUnifyTraitBoundWith(trait: RsTraitItem, other: RustType, project: Project): Boolean {
    return other is RustTraitType && other.trait.hierarchyContains(trait) ||
        other.getTraitsImplementedIn(project).contains(trait)
}
