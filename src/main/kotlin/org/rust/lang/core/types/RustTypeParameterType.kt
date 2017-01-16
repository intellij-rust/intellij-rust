package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.trait

data class RustTypeParameterType private constructor(
    private val parameter: TypeParameter
) : RustType {

    constructor(parameter: RsTypeParameter) : this(Named(parameter))

    constructor(trait: RsTraitItem) : this(Self(trait))

    override fun getTraitsImplementedIn(project: Project): Sequence<RsTraitItem> =
        parameter.bounds

    override fun getMethodsIn(project: Project): Sequence<RsFunction> =
        getTraitsImplementedIn(project).flatMap { it.functionList.asSequence() }

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
                    .filter { (it.type as? RsBaseType)?.path?.reference?.resolve() == parameter }
                    .flatMap { it.typeParamBounds?.polyboundList.orEmpty().asSequence() }

            return (parameter.typeParamBounds?.polyboundList.orEmpty().asSequence() + whereBounds)
                .mapNotNull { it.bound.traitRef?.trait }
        }
    }

    private data class Self(val trait: RsTraitItem) : TypeParameter {
        override val name: String? get() = "Self"

        override val bounds: Sequence<RsTraitItem> get() {
            val bounds = trait.typeParamBounds?.polyboundList.orEmpty().asSequence()
            return bounds.mapNotNull { it.bound.traitRef?.trait } + trait
        }
    }
}
