package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.flattenHierarchy
import org.rust.lang.core.psi.ext.resolveToBoundTrait
import org.rust.lang.core.types.BoundElement

data class TyTypeParameter private constructor(
    private val parameter: TypeParameter
) : Ty {

    constructor(parameter: RsTypeParameter) : this(Named(parameter))

    constructor(trait: RsTraitItem) : this(Self(trait))

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        parameter.bounds.flatMapTo(mutableSetOf()) { it.flattenHierarchy.asSequence() }

    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean {
        mapping?.merge(mutableMapOf(this to other))
        return true
    }

    override fun substitute(map: TypeArguments): Ty = map[this] ?: this

    override fun toString(): String = parameter.name ?: "<unknown>"

    private interface TypeParameter {
        val name: String?
        val bounds: Sequence<BoundElement<RsTraitItem>>
    }

    private data class Named(val parameter: RsTypeParameter) : TypeParameter {
        override val name: String? get() = parameter.name

        override val bounds: Sequence<BoundElement<RsTraitItem>> get() {
            val owner = parameter.parent?.parent as? RsGenericDeclaration
            val whereBounds =
                owner?.whereClause?.wherePredList.orEmpty()
                    .asSequence()
                    .filter { (it.typeReference as? RsBaseType)?.path?.reference?.resolve() == parameter }
                    .flatMap { it.typeParamBounds?.polyboundList.orEmpty().asSequence() }

            return (parameter.typeParamBounds?.polyboundList.orEmpty().asSequence() + whereBounds)
                .mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
        }
    }

    private data class Self(val trait: RsTraitItem) : TypeParameter {
        override val name: String? get() = "Self"

        override val bounds: Sequence<BoundElement<RsTraitItem>> get() = sequenceOf(BoundElement(trait))
    }
}
