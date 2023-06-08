/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.ide.fixes.ChangeToFieldShorthandFix
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.normType
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

    private fun buildForSmartPtr(ty: TyAdt, bindings: Map<String, RsPatBinding>): RsExpr {
        val item = ty.item
        val name = ty.item.name!!
        val parameter = ty.typeParameterValues[item.typeParameters[0]]!!
        return psiFactory.createAssocFunctionCall(name, "new", listOf(buildFor(parameter, bindings)))
    }

    fun buildFor(ty: Ty, bindings: Map<String, RsPatBinding>): RsExpr {
        return when (ty) {
            is TyBool -> psiFactory.createExpression("false")
            is TyInteger -> psiFactory.createExpression("0")
            is TyFloat -> psiFactory.createExpression("0.0")
            is TyChar -> psiFactory.createExpression("''")
            is TyReference -> when (ty.referenced) {
                is TyStr -> psiFactory.createExpression("\"\"")
                else -> psiFactory.createRefExpr(buildFor(ty.referenced, bindings), listOf(ty.mutability))
            }
            is TyAdt -> {
                val smartPointers = listOf(
                    items.Box,
                    items.Rc,
                    items.Arc,
                    items.Cell,
                    items.RefCell,
                    items.UnsafeCell,
                    items.Mutex
                )

                val item = ty.item
                if (item in smartPointers) {
                    return buildForSmartPtr(ty, bindings)
                }

                var default = this.defaultValue
                val implLookup = mod.implLookup
                if (implLookup.isDefault(ty).isTrue) {
                    default = psiFactory.createAssocFunctionCall("Default", "default", emptyList())
                }

                val name = item.name!! // `!!` is because it isn't possible to acquire TyAdt with anonymous item
                when (item) {
                    items.Option -> psiFactory.createExpression("None")
                    items.String -> psiFactory.createExpression("\"\".to_string()")
                    items.Vec -> psiFactory.createExpression("vec![]")
                    is RsStructItem -> if (item.kind == RsStructKind.STRUCT && item.canBeInstantiatedIn(mod)) {
                        if (implLookup.isDefault(ty).isTrue) {
                            return default
                        }

                        when {
                            item.blockFields != null -> {
                                val structLiteral = psiFactory.createStructLiteral(name)
                                if (recursive) {
                                    fillStruct(
                                        structLiteral.structLiteralBody,
                                        item.namedFields,
                                        item.namedFields,
                                        bindings
                                    )
                                }
                                structLiteral
                            }
                            item.tupleFields != null -> {
                                val argExprs = if (recursive) {
                                    item.positionalFields
                                        .map { it.typeReference.normType(implLookup) }
                                        .map { buildFor(it, bindings) }
                                } else {
                                    emptyList()
                                }
                                psiFactory.createFunctionCall(name, argExprs)
                            }
                            else -> psiFactory.createExpression(name)
                        }
                    } else {
                        default
                    }
                    is RsEnumItem -> {
                        if (implLookup.isDefault(ty).isTrue) {
                            return default
                        }

                        val variantWithoutFields = item.enumBody
                            ?.enumVariantList
                            ?.find { it.isFieldless }
                            ?.name
                        variantWithoutFields?.let { psiFactory.createExpression("$name::$it") }
                            ?: default
                    }
                    else -> default
                }
            }
            is TySlice, is TyArray -> psiFactory.createExpression("[]")
            is TyTuple -> {
                val text = ty.types.joinToString(prefix = "(", separator = ", ", postfix = ")") { tupleElement ->
                    buildFor(tupleElement, bindings).text
                }
                psiFactory.createExpression(text)
            }
            else -> defaultValue
        }
    }

    fun fillStruct(
        structLiteral: RsStructLiteralBody,
        declaredFields: List<RsFieldDecl>,
        fieldsToAdd: List<RsFieldDecl>,
        bindings: Map<String, RsPatBinding>
    ): List<RsStructLiteralField> {
        val forceMultiLine = structLiteral.structLiteralFieldList.isEmpty() && fieldsToAdd.size > 2
        val isMultiline = forceMultiLine || structLiteral.textContains('\n')

        val addedFields = mutableListOf<RsStructLiteralField>()
        for (fieldDecl in fieldsToAdd) {
            val field = findLocalBinding(fieldDecl, bindings)
                ?: specializedCreateStructLiteralField(fieldDecl, bindings)
                ?: continue
            val addBefore = findPlaceToAdd(field, structLiteral.structLiteralFieldList, declaredFields)
            val added = if (addBefore == null) {
                ensureTrailingComma(structLiteral.structLiteralFieldList)
                val added = structLiteral.addBefore(field, structLiteral.rbrace) as RsStructLiteralField
                if (isMultiline && fieldDecl == fieldsToAdd.last()) {
                    structLiteral.addAfter(psiFactory.createComma(), added)
                }
                added
            } else {
                val comma = structLiteral.addBefore(psiFactory.createComma(), addBefore)
                structLiteral.addBefore(field, comma) as RsStructLiteralField
            }
            addedFields.add(added)
        }

        if (forceMultiLine) {
            structLiteral.addAfter(psiFactory.createNewline(), structLiteral.lbrace)
        }

        return addedFields
    }

    private fun findLocalBinding(fieldDecl: RsFieldDecl, bindings: Map<String, RsPatBinding>): RsStructLiteralField? {
        val name = fieldDecl.name ?: return null
        val type = fieldDecl.typeReference?.normType ?: return null

        val binding = bindings[name] ?: return null
        val escapedName = fieldDecl.escapedName ?: return null
        return when {
            type.isEquivalentTo(binding.type) -> {
                val field = psiFactory.createStructLiteralField(escapedName, psiFactory.createExpression(escapedName))
                ChangeToFieldShorthandFix.applyShorthandInit(field)
                field
            }
            isRefContainer(type, binding.type) -> {
                val expr = buildReference(type, psiFactory.createExpression(escapedName))
                psiFactory.createStructLiteralField(escapedName, expr)
            }
            else -> null
        }
    }

    private fun isRefContainer(container: Ty, type: Ty): Boolean {
        return when (container) {
            type -> true
            is TyReference -> isRefContainer(container.referenced, type)
            else -> false
        }
    }

    private fun buildReference(type: Ty, expr: RsExpr): RsExpr {
        return when (type) {
            is TyReference -> {
                val inner = type.referenced
                psiFactory.createRefExpr(buildReference(inner, expr), listOf(type.mutability))
            }
            else -> expr
        }
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

    private fun specializedCreateStructLiteralField(fieldDecl: RsFieldDecl, bindings: Map<String, RsPatBinding>): RsStructLiteralField? {
        val fieldName = fieldDecl.escapedName ?: return null
        val fieldType = fieldDecl.typeReference?.normType ?: return null
        val fieldLiteral = buildFor(fieldType, bindings)
        return psiFactory.createStructLiteralField(fieldName, fieldLiteral)
    }
}
