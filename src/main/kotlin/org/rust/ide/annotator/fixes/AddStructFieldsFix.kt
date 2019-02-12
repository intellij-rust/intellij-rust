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
import org.rust.lang.core.psi.RsDefaultValueBuilder
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructLiteral
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.fields
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.ref.deepResolve

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
        val defaultValueBuilder = RsDefaultValueBuilder(decl.knownItems, body.containingMod, RsPsiFactory(project), recursive)
        val firstAdded = defaultValueBuilder.fillStruct(
            body,
            decl.fields,
            fieldsToAdd
        )

        if (editor != null && firstAdded != null) {
            val expr = firstAdded.expr
            if (expr != null) editor.caretModel.moveToOffset(expr.textOffset)
        }
    }
}
