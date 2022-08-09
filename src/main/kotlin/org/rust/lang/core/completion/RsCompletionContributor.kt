/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.RsPsiPattern.declarationPattern
import org.rust.lang.core.RsPsiPattern.inherentImplDeclarationPattern
import org.rust.lang.core.completion.lint.RsClippyLintCompletionProvider
import org.rust.lang.core.completion.lint.RsRustcLintCompletionProvider
import org.rust.lang.core.completion.sort.RS_COMPLETION_WEIGHERS
import org.rust.lang.core.completion.sort.RsCompletionWeigher
import org.rust.lang.core.or
import org.rust.stdext.exhaustive

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
        private val RS_COMPLETION_WEIGHERS_GROUPED: List<AnchoredWeigherGroup> = splitIntoGroups(RS_COMPLETION_WEIGHERS)

        /**
         * Applies [RS_COMPLETION_WEIGHERS] to [result]
         */
        fun withRustSorter(parameters: CompletionParameters, result: CompletionResultSet): CompletionResultSet {
            var sorter = (CompletionSorter.defaultSorter(parameters, result.prefixMatcher) as CompletionSorterImpl)
                .withoutClassifiers { it.id == "liftShorter" }
            for (weigherGroups in RS_COMPLETION_WEIGHERS_GROUPED) {
                sorter = sorter.weighAfter(weigherGroups.anchor, *weigherGroups.weighers)
            }
            return result.withRelevanceSorter(sorter)
        }

        private fun splitIntoGroups(weighersWithAnchors: List<Any>): List<AnchoredWeigherGroup> {
            val firstEntry = weighersWithAnchors.firstOrNull() ?: return emptyList()
            check(firstEntry is String) {
                "The first element in the weigher list must be a string placeholder like \"priority\"; " +
                    "actually it is `${firstEntry}`"
            }
            val result = mutableListOf<AnchoredWeigherGroup>()
            val weigherIds = hashSetOf<String>()
            var currentAnchor: String = firstEntry
            var currentWeighers = mutableListOf<LookupElementWeigher>()
            // Add "dummy weigher" in order to execute `is String ->` arm in the last iteration
            for (weigherOrAnchor in weighersWithAnchors.asSequence().drop(1).plus(sequenceOf("dummy weigher"))) {
                when (weigherOrAnchor) {
                    is String -> {
                        if (currentWeighers.isNotEmpty()) {
                            result += AnchoredWeigherGroup(currentAnchor, currentWeighers.toTypedArray())
                            currentWeighers = mutableListOf()
                        }
                        currentAnchor = weigherOrAnchor
                    }
                    is RsCompletionWeigher -> {
                        if (!weigherIds.add(weigherOrAnchor.id)) {
                            error("Found a ${RsCompletionWeigher::class.simpleName}.id duplicate: " +
                                "`${weigherOrAnchor.id}`")
                        }
                        currentWeighers += RsCompletionWeigherAsLookupElementWeigher(weigherOrAnchor)
                    }
                    else -> error("The weigher list must consists of String placeholders and instances of " +
                        "${RsCompletionWeigher::class.simpleName}, got ${weigherOrAnchor.javaClass}")
                }.exhaustive
            }
            return result
        }

        private class AnchoredWeigherGroup(val anchor: String, val weighers: Array<LookupElementWeigher>)

        private class RsCompletionWeigherAsLookupElementWeigher(
            private val weigher: RsCompletionWeigher
        ) : LookupElementWeigher(weigher.id, /* negated = */ false, /* dependsOnPrefix = */ false) {
            override fun weigh(element: LookupElement): Comparable<*> {
                val rsElement = element.`as`(RsLookupElement::class.java)
                return weigher.weigh(rsElement ?: element)
            }
        }
    }
}
