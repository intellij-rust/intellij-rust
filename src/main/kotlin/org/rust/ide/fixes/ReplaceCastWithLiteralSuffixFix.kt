/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsCastExpr
import org.rust.lang.core.psi.RsPsiFactory

open class ReplaceCastWithLiteralSuffixFix(
    element: RsCastExpr
) : RsQuickFixBase<RsCastExpr>(element) {
    @IntentionName
    private val fixText: String = RsBundle.message("intention.name.replace.with.0.1", element.expr.text, element.typeReference.text)
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.replace.cast.with.literal.suffix")
    override fun getText(): String = fixText
    override fun invoke(project: Project, editor: Editor?, element: RsCastExpr) {
        val psiFactory = RsPsiFactory(project)
        element.replace(psiFactory.createExpression(element.expr.text + element.typeReference.text))
    }
}
