/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.RsPsiPattern.declarationPattern
import org.rust.lang.core.RsPsiPattern.inherentImplDeclarationPattern
import org.rust.lang.core.completion.lint.RsClippyLintCompletionProvider
import org.rust.lang.core.completion.lint.RsRustcLintCompletionProvider
import org.rust.lang.core.or

class RsCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, RsPrimitiveTypeCompletionProvider)
        extend(CompletionType.BASIC, RsBoolCompletionProvider)
        extend(CompletionType.BASIC, RsFragmentSpecifierCompletionProvider)
        extend(CompletionType.BASIC, RsCommonCompletionProvider)
        extend(CompletionType.BASIC, RsTupleFieldCompletionProvider)
        extend(CompletionType.BASIC, RsDeriveCompletionProvider)
        extend(CompletionType.BASIC, RsAttributeCompletionProvider)
        extend(CompletionType.BASIC, RsMacroCompletionProvider)
        extend(CompletionType.BASIC, RsPartialMacroArgumentCompletionProvider)
        extend(CompletionType.BASIC, RsFullMacroArgumentCompletionProvider)
        extend(CompletionType.BASIC, RsCfgAttributeCompletionProvider)
        extend(CompletionType.BASIC, RsAwaitCompletionProvider)
        extend(CompletionType.BASIC, RsStructPatRestCompletionProvider)
        extend(CompletionType.BASIC, RsClippyLintCompletionProvider)
        extend(CompletionType.BASIC, RsRustcLintCompletionProvider)
        extend(CompletionType.BASIC, RsImplTraitMemberCompletionProvider)
        extend(CompletionType.BASIC, RsVisRestrictionCompletionProvider)
        extend(CompletionType.BASIC, RsLambdaExprCompletionProvider)
        extend(CompletionType.BASIC, RsPsiPattern.fieldVisibility, RsVisibilityCompletionProvider())
        extend(CompletionType.BASIC, declarationPattern() or inherentImplDeclarationPattern(), RsVisibilityCompletionProvider())
        extend(CompletionType.BASIC, RsReprCompletionProvider)
        extend(CompletionType.BASIC, RsCrateTypeAttrCompletionProvider)
        extend(CompletionType.BASIC, RsExternAbiCompletionProvider)
    }

    fun extend(type: CompletionType?, provider: RsCompletionProvider) {
        extend(type, provider.elementPattern, provider)
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        super.fillCompletionVariants(parameters, withRustSorter(parameters, result))
    }

    companion object {
        fun withRustSorter(parameters: CompletionParameters, result: CompletionResultSet): CompletionResultSet {
            val sorter = (CompletionSorter.defaultSorter(parameters, result.prefixMatcher) as CompletionSorterImpl)
                .withoutClassifiers { it.id == "liftShorter" }
            return result.withRelevanceSorter(sorter)
        }
    }
}
