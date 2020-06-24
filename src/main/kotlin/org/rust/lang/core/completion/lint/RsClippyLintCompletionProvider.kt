/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion.lint

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RsPath

object RsClippyLintCompletionProvider : RsLintCompletionProvider() {
    override val prefix: String = "clippy::"
    override val lints: List<Lint> = CLIPPY_LINTS

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        super.addCompletions(parameters, context, result)

        val path = parameters.position.parentOfType<RsPath>() ?: return
        if (getPathPrefix(path).isEmpty()) {
            addLintToCompletion(result, Lint("clippy", true), prefix)
        }
    }
}
