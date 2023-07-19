/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.RsPsiPattern
import org.rust.toml.completion.RustStringLiteralInsertionHandler

object RsCfgPanicCompletionProvider : RsCompletionProvider() {
    override val elementPattern: ElementPattern<out PsiElement>
        get() = RsPsiPattern.insideAnyCfgFlagValue("panic")

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        for (value in listOf("abort", "unwind")) {
            result.addElement(LookupElementBuilder.create(value).withInsertHandler(RustStringLiteralInsertionHandler()))
        }
    }
}
