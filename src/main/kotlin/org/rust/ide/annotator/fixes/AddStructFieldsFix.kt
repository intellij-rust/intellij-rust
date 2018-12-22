/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.annotator.calculateMissingFields
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsStructKind.STRUCT
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

/**
 * Adds the given fields to the stricture defined by `expr`
 */
class AddStructFieldsFix(
    structBody: RsStructLiteral,
    private val recursive: Boolean = false
) : LocalQuickFixAndIntentionActionOnPsiElement(structBody) {
    override fun getText(): String {
        return if (recursive) {
            "Recursively add missing fields"
        } else {
            "Add missing fields"
        }
    }

    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val structLiteral = startElement as RsStructLiteral
        val decl = structLiteral.path.reference.deepResolve() as? RsFieldsOwner ?: return
        val body = structLiteral.structLiteralBody
        val fieldsToAdd = calculateMissingFields(body, decl)
        val firstAdded = fillStruct(
            RsPsiFactory(project),
            decl.knownItems,
            body,
            decl.fields,
            fieldsToAdd,
            body.containingMod
        )

        if (editor != null && firstAdded != null) {
            val expr = firstAdded.expr
            if (expr != null) editor.caretModel.moveToOffset(expr.textOffset)
        }
    }

    private fun fillStruct(
        psiFactory: RsPsiFactory,
        items: KnownItems,
        structLiteral: RsStructLiteralBody,
        declaredFields: List<RsFieldDecl>,
        fieldsToAdd: List<RsFieldDecl>,
        mod: RsMod
    ): RsStructLiteralField? {
        val forceMultiLine = structLiteral.structLiteralFieldList.isEmpty() && fieldsToAdd.size > 2

        var firstAdded: RsStructLiteralField? = null
        for (fieldDecl in fieldsToAdd) {
            val field = specializedCreateStructLiteralField(psiFactory, items, fieldDecl, mod) ?: continue
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

    private fun specializedCreateStructLiteralField(
        factory: RsPsiFactory,
        items: KnownItems,
        fieldDecl: RsFieldDecl,
        mod: RsMod
    ): RsStructLiteralField? {
        val fieldName = fieldDecl.name ?: return null
        val fieldType =  fieldDecl.typeReference?.type ?: return null
        val fieldLiteral = defaultValueExprFor(factory, items, mod, fieldType)
        return factory.createStructLiteralField(fieldName, fieldLiteral)
    }

    private fun defaultValueExprFor(factory: RsPsiFactory, items: KnownItems, mod: RsMod, ty: Ty): RsExpr {
        val defaultValue = { factory.createExpression("()") }
        return when (ty) {
            is TyBool -> factory.createExpression("false")
            is TyInteger -> factory.createExpression("0")
            is TyFloat -> factory.createExpression("0.0")
            is TyChar -> factory.createExpression("''")
            is TyReference -> when (ty.referenced) {
                is TyStr -> factory.createExpression("\"\"")
                else -> factory.createRefExpr(defaultValueExprFor(factory, items, mod, ty.referenced), listOf(ty.mutability))
            }
            is TyAdt -> {
                val item = ty.item
                val name = item.name!! // `!!` is because it isn't possible to acquire TyAdt with anonymous item
                when (item) {
                    items.Option -> factory.createExpression("None")
                    items.String -> factory.createExpression("\"\".to_string()")
                    items.Vec -> factory.createExpression("vec![]")
                    is RsStructItem -> if (item.kind == STRUCT && item.canBeInstantiatedIn(mod)) {
                        when {
                            item.blockFields != null -> {
                                val structLiteral = factory.createStructLiteral(name)
                                if (recursive) {
                                    fillStruct(
                                        factory,
                                        items,
                                        structLiteral.structLiteralBody,
                                        item.namedFields,
                                        item.namedFields,
                                        mod
                                    )
                                }
                                structLiteral
                            }
                            item.tupleFields != null -> {
                                val argExprs = if (recursive) {
                                    item.positionalFields
                                        .map { it.typeReference.type }
                                        .map { defaultValueExprFor(factory, items, mod, it) }
                                } else {
                                    emptyList()
                                }
                                factory.createFunctionCall(name, argExprs)
                            }
                            else -> factory.createExpression(name)
                        }
                    } else {
                        defaultValue()
                    }
                    is RsEnumItem -> {
                        val variantWithoutFields = item.enumBody
                            ?.enumVariantList
                            ?.find { it.isFieldless }
                            ?.name
                        variantWithoutFields?.let { factory.createExpression("$name::$it") }
                            ?: defaultValue()
                    }
                    else -> defaultValue()
                }
            }
            is TySlice, is TyArray -> factory.createExpression("[]")
            is TyTuple -> {
                val text = ty.types.joinToString(prefix = "(", separator = ", ", postfix = ")") { tupleElement ->
                    defaultValueExprFor(factory, items, mod, tupleElement).text
                }
                factory.createExpression(text)
            }
            else -> defaultValue()
        }
    }
}
