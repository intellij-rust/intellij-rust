/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.rawValueParameters

class AddSelfFix(function: RsFunction) : RsQuickFixBase<RsFunction>(function) {
    override fun getFamilyName() = "Add self to function"

    override fun getText() = "Add self to function"

    override fun invoke(project: Project, editor: Editor?, element: RsFunction) {
        val hasParameters = element.rawValueParameters.isNotEmpty()
        val psiFactory = RsPsiFactory(project)

        val valueParameterList = element.valueParameterList
        val lparen = valueParameterList?.firstChild

        val self = psiFactory.createSelfReference()

        valueParameterList?.addAfter(self, lparen)
        if (hasParameters) {
            val parent = lparen?.parent
            parent?.addAfter(psiFactory.createComma(), parent.firstChild.nextSibling)
        }
    }
}
