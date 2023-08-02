/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.ide.annotator.format.FormatParameter
import org.rust.ide.annotator.format.baseType
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.findPreviewCopyIfNeeded
import org.rust.lang.core.types.type
import org.rust.openapiext.createSmartPointer
import org.rust.openapiext.document

class DeriveDebugAndChangeFormatToDebugFix(
    expr: RsExpr,
    parameter: FormatParameter.Value,
) : RsQuickFixBase<RsExpr>(expr) {
    @Nls
    private val _text = RsBundle.message(
        "intention.name.derive.debug.and.replace.display.to.debug",
        requireNotNull(expr.type.baseType()?.name),
    )

    @SafeFieldForPreview
    private val argument = SmartPointerManager
        .getInstance(expr.project)
        .createSmartPsiFileRangePointer(expr.containingFile, parameter.range)

    override fun getFamilyName() = _text
    override fun getText() = _text

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        val adt = element.type.baseType()!!.findPreviewCopyIfNeeded().createSmartPointer()
        val range = argument.range ?: return
        val document = element.containingFile.document ?: return
        document.replaceString(range.startOffset, range.endOffset, "{:?}")
        PsiDocumentManager.getInstance(project).commitDocument(document)
        DeriveTraitsFix.invoke(adt.element ?: return, "Debug")
    }
}
