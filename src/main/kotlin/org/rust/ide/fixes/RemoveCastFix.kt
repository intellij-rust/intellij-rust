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

open class RemoveCastFix(
    element: RsCastExpr
) : RsQuickFixBase<RsCastExpr>(element) {
    @IntentionName
    private val fixText: String = RsBundle.message("intention.name.remove.as", element.typeReference.text)
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.remove.unnecessary.cast")
    override fun getText(): String = fixText
    override fun invoke(project: Project, editor: Editor?, element: RsCastExpr) {
        element.replace(element.expr)
    }
}
