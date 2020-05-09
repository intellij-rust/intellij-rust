/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFieldLookup
import org.rust.lang.core.psi.ext.findAssociatedType
import org.rust.lang.core.psi.ext.isEdition2018
import org.rust.lang.core.psi.ext.receiver
import org.rust.lang.core.psi.ext.withSubst
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

object RsAwaitCompletionProvider : RsCompletionProvider() {

    private val AWAIT_TY: Key<Ty> = Key.create("AWAIT_TY")

    override val elementPattern: ElementPattern<out PsiElement>
        get() {
            val parent = psiElement<RsFieldLookup>()
                .with(object : PatternCondition<RsFieldLookup>("RsPostfixAwait") {
                    override fun accepts(t: RsFieldLookup, context: ProcessingContext?): Boolean {
                        if (context == null || !t.isEdition2018) return false
                        val receiver = t.receiver.safeGetOriginalOrSelf()
                        val lookup = ImplLookup.relativeTo(receiver)
                        val awaitTy = receiver.type.lookupFutureOutputTy(lookup)
                        if (awaitTy is TyUnknown) return false
                        context.put(AWAIT_TY, awaitTy)
                        return true
                    }
                })

            return PlatformPatterns.psiElement(RsElementTypes.IDENTIFIER).withParent(parent)
        }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val awaitTy = context.get(AWAIT_TY) ?: return
        val awaitBuilder = LookupElementBuilder
            .create("await")
            .bold()
            .withTypeText(awaitTy.toString())
        result.addElement(awaitBuilder.withPriority(KEYWORD_PRIORITY * 1.0001))
    }

    private fun Ty.lookupFutureOutputTy(lookup: ImplLookup): Ty {
        val futureTrait = lookup.items.Future ?: return TyUnknown
        val outputType = futureTrait.findAssociatedType("Output") ?: return TyUnknown
        val selection = lookup.selectProjectionStrict(TraitRef(this, futureTrait.withSubst()), outputType)
        return selection.ok()?.value ?: TyUnknown
    }
}
