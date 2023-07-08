/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsValueArgumentList
import org.rust.lang.core.psi.ext.deleteWithSurroundingCommaAndWhitespace

class RemoveRedundantFunctionArgumentsFix(
    element: RsValueArgumentList,
    private val expectedCount: Int
) : RsQuickFixBase<RsValueArgumentList>(element) {
    override fun getText(): String = RsBundle.message("intention.name.remove.redundant.arguments")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsValueArgumentList) {
        val extraArgs = element.exprList.drop(expectedCount)
        for (arg in extraArgs) {
            arg.deleteWithSurroundingCommaAndWhitespace()
        }
    }
}
