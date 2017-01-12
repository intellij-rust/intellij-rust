package org.rust.lang.core.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustTraitItemElement

interface RustPrimitiveType : RustType {

    override fun getTraitsImplementedIn(project: Project): Sequence<RustTraitItemElement> =
        emptySequence()

    companion object {
        fun fromTypeName(name: String): RustPrimitiveType? {
            val integerKind = RustIntegerType.Kind.values().find { it.name == name }
            if (integerKind != null) return RustIntegerType(integerKind)

            val floatKind = RustFloatType.Kind.values().find { it.name == name }
            if (floatKind != null) return RustFloatType(floatKind)

            return when (name) {
                "bool" -> RustBooleanType
                "char" -> RustCharacterType
                "str" -> RustStringSliceType
                else -> null
            }
        }
    }
}
