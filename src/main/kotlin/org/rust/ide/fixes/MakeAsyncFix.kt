/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsFunctionOrLambda
import org.rust.lang.core.psi.ext.isAsync

class MakeAsyncFix(function: RsFunctionOrLambda) : RsQuickFixBase<RsFunctionOrLambda>(function) {

    private val isFunction: Boolean = function is RsFunction

    override fun getText(): String {
        val item = if (isFunction) RsBundle.message("intention.name.function") else RsBundle.message("intention.name.lambda")
        return RsBundle.message("intention.name.make.async", item)
    }

    override fun getFamilyName(): String = RsBundle.message("intention.family.name.make.async")

    override fun invoke(project: Project, editor: Editor?, element: RsFunctionOrLambda) {
        if (element.isAsync) return
        val anchor = when (element) {
            is RsFunction -> element.unsafe ?: element.externAbi ?: element.fn
            is RsLambdaExpr -> element.move ?: element.valueParameterList
            else -> error("unreachable")
        }
        element.addBefore(RsPsiFactory(project).createAsyncKeyword(), anchor)
    }
}
