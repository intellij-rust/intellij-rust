package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType

class RustFieldReferenceImpl(
    field: RustFieldName
) : RustReferenceBase<RustFieldName>(field, field.identifier)
  , RustReference {

    override fun getVariants(): Array<out Any> = visibleFields().filter { it.name != null }.toTypedArray()

    override fun resolve(): RustNamedElement? = visibleFields().find { it.name == element.name }

    private fun visibleFields(): List<RustNamedElement> {
        val structLiteral = element.parentOfType<RustStructExpr>() ?: return emptyList()
        val structOrEnum = structLiteral.path.reference.resolve() ?: return emptyList()
        return when (structOrEnum) {
            is RustStructItem  -> structOrEnum.structDeclArgs?.fieldDeclList
            is RustEnumVariant -> structOrEnum.enumStructArgs?.fieldDeclList
            else               -> null
        }.orEmpty()
    }
}
