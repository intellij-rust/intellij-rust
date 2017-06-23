package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitRef
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
    constructor(trait: RsTraitItem, target: String) : this(AssociatedType(trait, target))

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        parameter.bounds.flatMapTo(mutableSetOf()) { it.flattenHierarchy.asSequence() }

    fun getTraitRefs(): Sequence<RsTraitRef> = parameter.traitRefs

    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean {
        mapping?.merge(mutableMapOf(this to other))
        return true
    }

    override fun substitute(map: TypeArguments): Ty = map[this] ?: this

    override fun toString(): String = parameter.name ?: "<unknown>"

    private interface TypeParameter {
        val name: String?
        val bounds: Sequence<BoundElement<RsTraitItem>>
        val traitRefs: Sequence<RsTraitRef>
    }

    private data class Named(val parameter: RsTypeParameter) : TypeParameter {
        override val name: String? get() = parameter.name

        override val bounds: Sequence<BoundElement<RsTraitItem>> get() {
            return traitRefs.mapNotNull { it.resolveToBoundTrait }
        }

        override val traitRefs: Sequence<RsTraitRef> get() {
            val owner = parameter.parent?.parent as? RsGenericDeclaration
            val whereBounds =
                owner?.whereClause?.wherePredList.orEmpty()
                    .asSequence()
                    .filter { (it.typeReference as? RsBaseType)?.path?.reference?.resolve() == parameter }
                    .flatMap { it.typeParamBounds?.polyboundList.orEmpty().asSequence() }

            return (parameter.typeParamBounds?.polyboundList.orEmpty().asSequence() + whereBounds)
                .mapNotNull { it.bound.traitRef }
        }

    }

    private data class Self(val trait: RsTraitItem) : TypeParameter {
        override val name: String? get() = "Self"

        override val traitRefs: Sequence<RsTraitRef> get() = emptySequence()
        override val bounds: Sequence<BoundElement<RsTraitItem>> get() = sequenceOf(BoundElement(trait))
    }

    private data class AssociatedType(val trait: RsTraitItem, val target: String) : TypeParameter {
        override val name: String? get() = target

        override val traitRefs: Sequence<RsTraitRef> get() = emptySequence()
        override val bounds: Sequence<BoundElement<RsTraitItem>> get() = sequenceOf(BoundElement(trait))

    }
}
