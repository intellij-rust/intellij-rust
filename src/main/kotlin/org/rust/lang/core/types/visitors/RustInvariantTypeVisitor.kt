package org.rust.lang.core.types.visitors

import org.rust.lang.core.types.*

interface RustInvariantTypeVisitor<T> {

    fun visitUnitType(type: RustUnitType): T

    fun visitUnknown(type: RustUnknownType): T

    fun visitInteger(type: RustIntegerType): T

    fun visitFloat(type: RustFloatType): T

    fun visitString(type: RustStringSliceType): T

    fun visitChar(type: RustCharacterType): T

    fun visitBoolean(type: RustBooleanType): T

}
