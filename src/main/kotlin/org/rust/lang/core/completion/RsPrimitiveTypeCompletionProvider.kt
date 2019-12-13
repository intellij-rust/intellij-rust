/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.with
import org.rust.lang.core.withSuperParent
import org.rust.stdext.buildList

object RsPrimitiveTypeCompletionProvider : RsCompletionProvider() {

    private val primitives: List<String> = buildList {
        addAll(TyInteger.NAMES)
        addAll(TyFloat.NAMES)
        add(TyBool.name)
        add(TyStr.name)
        add(TyChar.name)
    }

    override val elementPattern: ElementPattern<PsiElement> get() = PlatformPatterns.psiElement()
        .withSuperParent<RsTypeReference>(3)
        .with("FirstChild") { e -> e.prevSibling == null }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        primitives.forEach {
            result.addElement(LookupElementBuilder.create(it).bold().withPriority(PRIMITIVE_TYPE_PRIORITY))
        }
    }
}
