/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.ext.deleteWithSurroundingCommaAndWhitespace

class RemoveStructLiteralFieldFix(
    field: RsStructLiteralField,
    private val removingFieldName: String = "`${field.text}`"
) : RsQuickFixBase<PsiElement>(field) {
    override fun getFamilyName() = "Remove struct literal field"

    override fun getText() = "Remove $removingFieldName"

    override fun invoke(project: Project, editor: Editor?, startElement: PsiElement) {
        val field = (startElement as? RsStructLiteralField) ?: return
        field.deleteWithSurroundingCommaAndWhitespace()
    }
}
