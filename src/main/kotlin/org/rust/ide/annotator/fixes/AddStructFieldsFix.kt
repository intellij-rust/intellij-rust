/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.annotator.calculateMissingFields
import org.rust.ide.formatter.impl.CommaList
import org.rust.ide.formatter.processors.removeTrailingComma
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.namedFields
import org.rust.lang.core.resolve.StdKnownItems
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
        val psiFactory = RsPsiFactory(project)
        val structLiteral = startElement as RsStructLiteral
        val decl = structLiteral.path.reference.resolve() as? RsFieldsOwner ?: return
        val body = structLiteral.structLiteralBody
        val fieldsToAdd = calculateMissingFields(body, decl)
        val (firstAdded, _) = fillStruct(psiFactory, body, decl.namedFields, fieldsToAdd, postProcess = true)

        if (editor != null && firstAdded != null) {
            editor.caretModel.moveToOffset(firstAdded.expr!!.textOffset)
        }
    }

    private fun fillStruct(
        psiFactory: RsPsiFactory,
        structLiteralBody: RsStructLiteralBody,
        declaredFields: List<RsFieldDecl>,
        fieldsToAdd: List<RsFieldDecl>,
        postProcess: Boolean
    ): Pair<RsStructLiteralField?, RsStructLiteralBody> {
        var structLiteral = structLiteralBody
        val forceMultiline = structLiteral.structLiteralFieldList.isEmpty() && fieldsToAdd.size > 2

        var firstAdded: RsStructLiteralField? = null
        for (fieldDecl in fieldsToAdd) {
            val field = specializedCreateStructLiteralField(psiFactory, fieldDecl)!!
            val addBefore = findPlaceToAdd(field, structLiteral.structLiteralFieldList, declaredFields)
            ensureTrailingComma(structLiteral.structLiteralFieldList)

            val comma = structLiteral.addBefore(psiFactory.createComma(), addBefore ?: structLiteral.rbrace)
            val added = structLiteral.addBefore(field, comma) as RsStructLiteralField

            if (firstAdded == null) {
                firstAdded = added
            }
        }

        if (forceMultiline) {
            structLiteral.addAfter(psiFactory.createNewline(), structLiteral.lbrace)
        }

        if (postProcess) structLiteral = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(structLiteral)

        CommaList.forElement(structLiteral.elementType)!!
            .removeTrailingComma(structLiteral)

        return Pair(firstAdded, structLiteral)
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

    private fun defaultValueExprFor(factory: RsPsiFactory, fieldDecl: RsFieldDecl, ty: Ty): RsExpr {
        val stdknownItems = StdKnownItems.relativeTo(fieldDecl)
        return when (ty) {
            is TyBool -> factory.createExpression("false")
            is TyInteger -> factory.createExpression("0")
            is TyFloat -> factory.createExpression("0.0")
            is TyChar -> factory.createExpression("''")
            is TyStr -> factory.createExpression("\"\"")
            is TyReference ->
                defaultValueExprFor(factory, fieldDecl, ty.referenced)
            is TyStructOrEnumBase -> {
                when (ty.item) {
                    stdknownItems.findCoreItem("option::Option") -> factory.createExpression("None")
                    stdknownItems.findStdItem("collections", "string::String") -> factory.createExpression("String::new()")
                    stdknownItems.findStdItem("collections", "vec::Vec") -> factory.createExpression("Vec::new()")
                    else -> {
                        if (ty is TyStruct && recursive) {
                            val structLiteral = factory.createStructLiteral(ty.item.name!!)
                            fillStruct(
                                factory,
                                structLiteral.structLiteralBody,
                                ty.item.namedFields,
                                ty.item.namedFields,
                                postProcess = false
                            )
                            structLiteral
                        } else {
                            factory.createExpression("()")
                        }
                    }
                }
            }
            is TySlice, is TyArray -> factory.createExpression("[]")
            is TyTuple -> {
                val text = ty.types.joinToString(prefix = "(", separator = ", ", postfix = ")") { tupleElement ->
                    defaultValueExprFor(factory, fieldDecl, tupleElement).text
                }
                factory.createExpression(text)
            }
            else -> factory.createExpression("()")
        }
    }

    private fun specializedCreateStructLiteralField(factory: RsPsiFactory, fieldDecl: RsFieldDecl): RsStructLiteralField? {
        val fieldType = fieldDecl.typeReference?.type ?: return null
        val fieldLiteral = defaultValueExprFor(factory, fieldDecl, fieldType)
        return factory.createStructLiteralField(fieldDecl.name!!, fieldLiteral)
    }
}
