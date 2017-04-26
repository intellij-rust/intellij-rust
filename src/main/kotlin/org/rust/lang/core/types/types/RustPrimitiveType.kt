package org.rust.lang.core.types.types

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.types.RustType

interface RustPrimitiveType : RustType {

    override fun getTraitsImplementedIn(project: Project): Collection<RsTraitItem> =
        emptyList()

    override fun canUnifyWith(other: RustType, project: Project): Boolean =
        this == other

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
