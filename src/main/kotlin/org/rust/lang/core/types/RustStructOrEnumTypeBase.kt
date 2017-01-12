package org.rust.lang.core.types

import org.rust.lang.core.psi.RsStructOrEnumItemElement
import org.rust.lang.core.psi.typeParameters

interface RustStructOrEnumTypeBase : RustType {
    val typeArguments: List<RustType>

    val item: RsStructOrEnumItemElement

    override val typeParameterValues: Map<RustTypeParameterType, RustType>
        get() = item.typeParameters.zip(typeArguments)
            .mapNotNull {
                val (param, arg) = it
                RustTypeParameterType(param) to arg
            }.toMap()

}
