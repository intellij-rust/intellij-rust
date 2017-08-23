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
import org.rust.ide.formatter.RsTrailingCommaFormatProcessor
import org.rust.ide.formatter.impl.CommaList
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.elementType

/**
 * Adds the given fields to the stricture defined by `expr`
 */
class AddStructFieldsFix(
    private val declaredFields: List<RsFieldDecl>,
    private val fieldsToAdd: List<RsFieldDecl>,
    structBody: RsStructLiteralBody
) : LocalQuickFixAndIntentionActionOnPsiElement(structBody) {
    override fun getText(): String = "Add missing fields"

    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val psiFactory = RsPsiFactory(project)
        var structLiteral = startElement as RsStructLiteralBody

        val forceMultiline = structLiteral.structLiteralFieldList.isEmpty() && fieldsToAdd.size > 2

        var firstAdded: RsStructLiteralField? = null
        for (fieldDecl in fieldsToAdd) {
            val field = psiFactory.createStructLiteralField(fieldDecl.name!!)
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

        structLiteral = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(structLiteral)
        RsTrailingCommaFormatProcessor.fixSingleLineBracedBlock(structLiteral, CommaList.forElement(structLiteral.elementType)!!)

        if (editor != null && firstAdded != null) {
            editor.caretModel.moveToOffset(firstAdded.expr!!.textOffset)
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
}
