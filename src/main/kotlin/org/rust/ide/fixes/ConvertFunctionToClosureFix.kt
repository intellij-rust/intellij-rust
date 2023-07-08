/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.intentions.ConvertFunctionToClosureIntention
import org.rust.lang.core.psi.RsFunction

class ConvertFunctionToClosureFix(function: RsFunction) : RsQuickFixBase<RsFunction>(function) {

    override fun getText(): String = RsBundle.message("intention.name.convert.function.to.closure")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsFunction) {
        ConvertFunctionToClosureIntention().doInvoke(project, editor, element)
    }
}
