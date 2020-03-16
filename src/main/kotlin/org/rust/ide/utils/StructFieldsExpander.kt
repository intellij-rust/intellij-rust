/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.rust.ide.annotator.calculateMissingFields
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.ref.deepResolve
import org.rust.openapiext.buildAndRunTemplate
import org.rust.openapiext.createSmartPointer

fun addMissingFieldsToStructLiteral(
    factory: RsPsiFactory,
    editor: Editor?,
    structLiteral: RsStructLiteral,
    recursive: Boolean = false
) {
    val declaration = structLiteral.path.reference?.deepResolve() as? RsFieldsOwner ?: return
    val body = structLiteral.structLiteralBody
    val fieldsToAdd = calculateMissingFields(body, declaration)
    val defaultValueBuilder = RsDefaultValueBuilder(declaration.knownItems, body.containingMod, factory, recursive)
    val addedFields = defaultValueBuilder.fillStruct(
        body,
        declaration.fields,
        fieldsToAdd,
        RsDefaultValueBuilder.getVisibleBindings(structLiteral)
    )
    editor?.buildAndRunTemplate(body, addedFields.mapNotNull { it.expr?.createSmartPointer() })
}

fun expandStructFields(factory: RsPsiFactory, patStruct: RsPatStruct) {
    val declaration = patStruct.path.reference?.deepResolve() as? RsFieldsOwner ?: return
    val hasTrailingComma = patStruct.rbrace.getPrevNonCommentSibling()?.elementType == RsElementTypes.COMMA
    patStruct.patRest?.delete()
    val existingFields = patStruct.patFieldList
    val bodyFieldNames = existingFields.map { it.kind.fieldName }.toSet()
    val missingFields = declaration.fields
        .filter { it.name !in bodyFieldNames && !it.queryAttributes.hasCfgAttr() }
        .map { factory.createPatField(it.name!!) }

    if (existingFields.isEmpty()) {
        addFieldsToPat(factory, patStruct, missingFields, hasTrailingComma)
        return
    }

    val fieldPositions = declaration.fields.withIndex().associate { it.value.name!! to it.index }
    var insertedFieldsAmount = 0
    for (missingField in missingFields) {
        val missingFieldPosition = fieldPositions[missingField.kind.fieldName]!!
        for (existingField in existingFields) {
            val existingFieldPosition = fieldPositions[existingField.kind.fieldName] ?: continue
            if (missingFieldPosition < existingFieldPosition) {
                patStruct.addBefore(missingField, existingField)
                patStruct.addAfter(factory.createComma(), existingField.getPrevNonCommentSibling())
                insertedFieldsAmount++
                break
            }
        }
    }
    addFieldsToPat(factory, patStruct, missingFields.drop(insertedFieldsAmount), hasTrailingComma)
}

fun expandTupleStructFields(factory: RsPsiFactory, editor: Editor?, patTuple: RsPatTupleStruct) {
    val declaration = patTuple.path.reference?.deepResolve() as? RsFieldsOwner ?: return
    val hasTrailingComma = patTuple.rparen.getPrevNonCommentSibling()?.elementType == RsElementTypes.COMMA
    val bodyFields = patTuple.childrenOfType<RsPatIdent>()
    val missingFieldsAmount = declaration.fields.size - bodyFields.size
    addFieldsToPat(factory, patTuple, createTupleStructMissingFields(factory, missingFieldsAmount), hasTrailingComma)
    patTuple.patRest?.delete()
    editor?.buildAndRunTemplate(patTuple, patTuple.childrenOfType<RsPatBinding>().map { it.createSmartPointer() })
}

private fun createTupleStructMissingFields(factory: RsPsiFactory, amount: Int): List<RsPatBinding> {
    val missingFields = ArrayList<RsPatBinding>(amount)
    for (i in 0 until amount) {
        missingFields.add(factory.createPatBinding("_$i"))
    }
    return missingFields
}

private fun addFieldsToPat(factory: RsPsiFactory, pat: RsPat, fields: List<PsiElement>, hasTrailingComma: Boolean) {
    var anchor = determineOrCreateAnchor(factory, pat)
    for (missingField in fields) {
        pat.addAfter(missingField, anchor)
        // Do not insert comma if we are in the middle of pattern
        // since it will cause double comma in patterns with a trailing comma.
        if (fields.last() == missingField) {
            if (anchor.nextSibling?.getNextNonCommentSibling() !is RsPatRest) {
                pat.addAfter(factory.createComma(), anchor.nextSibling)
            }
        } else {
            pat.addAfter(factory.createComma(), anchor.nextSibling)
        }
        anchor = anchor.nextSibling.nextSibling
    }
    if (!hasTrailingComma) {
        anchor.delete()
    }
}

private fun determineOrCreateAnchor(factory: RsPsiFactory, pat: RsPat): PsiElement {
    val patRest = pat.childrenOfType<RsPatRest>().firstOrNull()
    if (patRest != null) {
        // Picking prev sibling of '..' as anchor allows us to fill the pattern starting from '..' position
        // instead of filling pattern starting from the end.
        return patRest.getPrevNonCommentSibling()!!
    }
    val lastElementInBody = pat.lastChild.getPrevNonCommentSibling()!!
    return if (lastElementInBody !is LeafPsiElement) {
        pat.addAfter(factory.createComma(), lastElementInBody)
        lastElementInBody.nextSibling
    } else {
        lastElementInBody
    }
}
