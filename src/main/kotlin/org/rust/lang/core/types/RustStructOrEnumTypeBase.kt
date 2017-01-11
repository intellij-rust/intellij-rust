package org.rust.lang.core.types

import org.rust.lang.core.psi.RustStructOrEnumItemElement

abstract class RustStructOrEnumTypeBase(val typeArguments: List<RustType>) : RustTypeBase() {

    abstract val item: RustStructOrEnumItemElement

    override val typeParameterValues: Map<RustTypeParameterType, RustType>
        get() = item.typeParameterList?.typeParamList.orEmpty()
            .zip(typeArguments)
            .mapNotNull {
                val (param, arg) = it
                RustTypeParameterType(param) to arg
            }.toMap()

}
