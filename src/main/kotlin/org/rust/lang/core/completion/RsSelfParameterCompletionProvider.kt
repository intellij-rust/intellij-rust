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
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.or
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psiElement
import org.rust.lang.core.with

object RsSelfParameterCompletionProvider : RsCompletionProvider() {
    override val elementPattern: ElementPattern<PsiElement>
        get() {
            val firstParam = psiElement<RsValueParameter>()
                .with("isImplFirstParam") { param, _ ->
                    val paramList = param.context as? RsValueParameterList ?: return@with false
                    val function = paramList.context as? RsFunction ?: return@with false
                    param.pat == null
                        && function.owner is RsAbstractableOwner.Impl
                        && paramList.selfParameter== null
                        && paramList.valueParameterList.firstOrNull() == param
                }
            return RsPsiPattern.simplePathPattern.withParent(
                psiElement<RsPath>().withParent(
                    psiElement<RsPathType>().withParent(firstParam) or
                            psiElement<RsPathType>().withParent(psiElement<RsRefLikeType>().withParent(firstParam)))
            )
        }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = LookupElementBuilder.create("self")
            .bold()
            .withIcon(RsIcons.BINDING)
            .toKeywordElement()
        result.addElement(element)
    }
}
