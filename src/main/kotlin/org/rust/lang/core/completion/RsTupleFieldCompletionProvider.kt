/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFieldLookup
import org.rust.lang.core.psi.ext.parentDotExpr
import org.rust.lang.core.psi.ext.receiver
import org.rust.lang.core.psiElement
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.expectedType
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.type

object RsTupleFieldCompletionProvider : RsCompletionProvider() {

    private val TUPLE_FIELD_INFO: Key<Pair<RsFieldLookup, TyTuple>> = Key.create("TUPLE_FIELD_INFO")

    override val elementPattern: PsiElementPattern.Capture<PsiElement> get() {
        val parent = psiElement<RsFieldLookup>()
            .with(object : PatternCondition<RsFieldLookup>("TupleType") {
                override fun accepts(t: RsFieldLookup, context: ProcessingContext?): Boolean {
                    if (context == null) return false
                    val fieldLookup = t.safeGetOriginalOrSelf()
                    val type = fieldLookup.receiver.type as? TyTuple ?: return false
                    context.put(TUPLE_FIELD_INFO, fieldLookup to type)
                    return true
                }
            })

        return PlatformPatterns.psiElement(RsElementTypes.IDENTIFIER).withParent(parent)
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val (fieldLookup, type) = context[TUPLE_FIELD_INFO] ?: return

        val completionContext = RsCompletionContext(expectedTy = fieldLookup.parentDotExpr.expectedType)

        val elements = type.types.withIndex().map { (index, ty) ->
            createLookupElement(object : CompletionEntity {
                override val ty: Ty? get() = ty
                override val implLookup: ImplLookup get() = fieldLookup.implLookup
                override fun getBasePriority(context: RsCompletionContext): Double = FIELD_DECL_PRIORITY
                override fun createBaseLookupElement(context: RsCompletionContext): LookupElementBuilder {
                    return LookupElementBuilder
                        .create(index)
                        .bold()
                        .withTypeText(ty.toString())
                        .withIcon(RsIcons.FIELD)
                }

            }, completionContext)
        }
        result.addAllElements(elements)
    }
}
