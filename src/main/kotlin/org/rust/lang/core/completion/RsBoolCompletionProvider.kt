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
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.ty.TyBool

object RsBoolCompletionProvider : RsCompletionProvider() {

    override val elementPattern: ElementPattern<PsiElement> get() = RsPsiPattern.simplePathPattern

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (parameters.position.safeGetOriginalOrSelf().ancestorOrSelf<RsPathExpr>()?.expectedType == TyBool) {
            for (value in listOf("true", "false")) {
                result.addElement(LookupElementBuilder.create(value).bold().withPriority(KEYWORD_PRIORITY))
            }
        }
    }
}
