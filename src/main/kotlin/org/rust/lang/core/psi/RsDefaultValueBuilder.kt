/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

class RsDefaultValueBuilder(
    private val items: KnownItems,
    private val mod: RsMod,
    private val psiFactory: RsPsiFactory,
    private val recursive: Boolean = false
) {
    private val defaultValue: RsExpr
        get() = psiFactory.createExpression("()")

    fun buildFor(ty: Ty): RsExpr {
        return when (ty) {
            is TyBool -> psiFactory.createExpression("false")
            is TyInteger -> psiFactory.createExpression("0")
            is TyFloat -> psiFactory.createExpression("0.0")
            is TyChar -> psiFactory.createExpression("''")
            is TyReference -> when (ty.referenced) {
                is TyStr -> psiFactory.createExpression("\"\"")
                else -> psiFactory.createRefExpr(buildFor(ty.referenced), listOf(ty.mutability))
            }
            is TyAdt -> {
                val item = ty.item
                val name = item.name!! // `!!` is because it isn't possible to acquire TyAdt with anonymous item
                when (item) {
                    items.Option -> psiFactory.createExpression("None")
                    items.String -> psiFactory.createExpression("\"\".to_string()")
                    items.Vec -> psiFactory.createExpression("vec![]")
                    is RsStructItem -> if (item.kind == RsStructKind.STRUCT && item.canBeInstantiatedIn(mod)) {
                        when {
                            item.blockFields != null -> {
                                val structLiteral = psiFactory.createStructLiteral(name)
                                if (recursive) {
                                    fillStruct(
                                        structLiteral.structLiteralBody,
                                        item.namedFields,
                                        item.namedFields
                                    )
                                }
                                structLiteral
                            }
                            item.tupleFields != null -> {
                                val argExprs = if (recursive) {
                                    item.positionalFields
                                        .map { it.typeReference.type }
                                        .map { buildFor(it) }
                                } else {
                                    emptyList()
                                }
                                psiFactory.createFunctionCall(name, argExprs)
                            }
                            else -> psiFactory.createExpression(name)
                        }
                    } else {
                        defaultValue
                    }
                    is RsEnumItem -> {
                        val variantWithoutFields = item.enumBody
                            ?.enumVariantList
                            ?.find { it.isFieldless }
                            ?.name
                        variantWithoutFields?.let { psiFactory.createExpression("$name::$it") }
                            ?: defaultValue
                    }
                    else -> defaultValue
                }
            }
            is TySlice, is TyArray -> psiFactory.createExpression("[]")
            is TyTuple -> {
                val text = ty.types.joinToString(prefix = "(", separator = ", ", postfix = ")") { tupleElement ->
                    buildFor(tupleElement).text
                }
                psiFactory.createExpression(text)
            }
            else -> defaultValue
        }
    }

    fun fillStruct(
        structLiteral: RsStructLiteralBody,
        declaredFields: List<RsFieldDecl>,
        fieldsToAdd: List<RsFieldDecl>
    ): RsStructLiteralField? {
        val forceMultiLine = structLiteral.structLiteralFieldList.isEmpty() && fieldsToAdd.size > 2

        var firstAdded: RsStructLiteralField? = null
        for (fieldDecl in fieldsToAdd) {
            val field = specializedCreateStructLiteralField(fieldDecl) ?: continue
            val addBefore = findPlaceToAdd(field, structLiteral.structLiteralFieldList, declaredFields)
            val added = if (addBefore == null) {
                ensureTrailingComma(structLiteral.structLiteralFieldList)
                structLiteral.addBefore(field, structLiteral.rbrace) as RsStructLiteralField
            } else {
                val comma = structLiteral.addBefore(psiFactory.createComma(), addBefore)
                structLiteral.addBefore(field, comma) as RsStructLiteralField
            }

            if (firstAdded == null) {
                firstAdded = added
            }
        }

        if (forceMultiLine) {
            structLiteral.addAfter(psiFactory.createNewline(), structLiteral.lbrace)
        }

        return firstAdded
    }

    private fun findPlaceToAdd(
        fieldToAdd: RsStructLiteralField,
        existingFields: List<RsStructLiteralField>,
        declaredFields: List<RsFieldDecl>
    ): RsStructLiteralField? {
        // If `fieldToAdd` is first in the original declaration, add it first
        if (fieldToAdd.referenceName == declaredFields.firstOrNull()?.name) {
            return existingFields.firstOrNull()
        }

        // If it was last, add last
        if (fieldToAdd.referenceName == declaredFields.lastOrNull()?.name) {
            return null
        }

        val pos = declaredFields.indexOfFirst { it.name == fieldToAdd.referenceName }
        check(pos != -1)
        val prev = declaredFields[pos - 1]
        val next = declaredFields[pos + 1]
        val prevIdx = existingFields.indexOfFirst { it.referenceName == prev.name }
        val nextIdx = existingFields.indexOfFirst { it.referenceName == next.name }

        // Fit between two existing fields in the same order
        if (prevIdx != -1 && prevIdx + 1 == nextIdx) {
            return existingFields[nextIdx]
        }
        // We have next field, but the order is different.
        // It's impossible to guess the best position, so
        // let's add to the end
        if (nextIdx != -1) {
            return null
        }

        if (prevIdx != -1) {
            return existingFields.getOrNull(prevIdx + 1)
        }

        return null
    }

    private fun specializedCreateStructLiteralField(fieldDecl: RsFieldDecl): RsStructLiteralField? {
        val fieldName = fieldDecl.name ?: return null
        val fieldType = fieldDecl.typeReference?.type ?: return null
        val fieldLiteral = buildFor(fieldType)
        return psiFactory.createStructLiteralField(fieldName, fieldLiteral)
    }
}
