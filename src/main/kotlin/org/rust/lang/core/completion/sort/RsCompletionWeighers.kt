/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion.sort

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.completion.RsLookupElement
import org.rust.lang.core.completion.RsLookupElementProperties

/**
 * A list of weighers that sort completion variants in the Rust plugin.
 *
 * # Explanation
 *
 * Let's see how this list of weighers
 * ```
 * preferFalse(P::isMethodSelfTypeIncompatible, ...),
 * preferTrue(P::isReturnTypeConformsToExpectedType, ...),
 * ```
 *
 * will sort methods in this case
 *
 * ```
 * struct S;
 * impl S {
 *     fn f1(&mut self) -> i32 {}    // isMethodSelfTypeIncompatible = true,  isReturnTypeConformsToExpectedType = false
 *     fn f2(&mut self) -> String {} // isMethodSelfTypeIncompatible = true,  isReturnTypeConformsToExpectedType = true
 *     fn f3(&self) -> i32 {}        // isMethodSelfTypeIncompatible = false, isReturnTypeConformsToExpectedType = false
 *     fn f4(&self) -> String {}     // isMethodSelfTypeIncompatible = false, isReturnTypeConformsToExpectedType = true
 * }
 * fn foo(a: &S) {
 *     let b: String = a. // <-- complete here
 * }
 * ```
 * Initially, we have a completion list consisting of `[f1, f2, f3, f4]` methods.
 * The first weigher prefers methods with compatible receiver type, i.e. methods with
 * `isMethodSelfTypeIncompatible = false`, i.e. `f3` and `f4` (because `f1` and `f2` require `&mut self` but we
 * have `&S`, so inserting `f1` or `f2` will cause a compilation error). After applying this weigher,
 * the completion list will look like `[f3, f4, f1, f2]`. Note that `f3` and `f4` are now prioritized.
 * Next weighers can swap `f3` <-> `f4` and/or `f1` <-> `f2`, but not `f3` <-> `f1`, for example.
 * Effectively, the first weigher splits the list into two sublists: `[f3, f4]` and `[f1, f2]`, where
 * the list `[f3, f4]` is strictly more preferred than `[f1, f2]`, and next weighers can reorder elements only
 * within a sublist, but not between them.
 * The second weigher prefers methods that return a type that conforms to the expected type, i.e. `f2` and `f4`
 * (because in our case, these methods return `String` which is the expected type here).
 * So, it places `f2` before `f1` and `f4` before `f3`: `[f4, f3, f2, f1]`. Note that `f3` is still before `f2`.
 *
 * # Notes
 *
 * The [RS_COMPLETION_WEIGHERS] list consists of [RsCompletionWeigher] instances and string placeholders which
 * refer to IntelliJ platform weighers. Our weighers can be inserted after a specific IntelliJ platform weigher
 * (e.g. "priority" or "prefix").
 *
 * The list of weighers is not final - there can be other weighers registered with
 * `<com.intellij.weigher key="completion">` extension point.
 *
 * The IntelliJ platform provides other completion variants sorting mechanisms that may reorder
 * everything. For example, "Machine Learning-Assisted Completion".
 *
 * Values returned from these weighers are automatically used as "element features" in ML-Assisted Completion
 * (identified by [RsCompletionWeigher.id]), see [org.rust.ml.RsElementFeatureProvider] for more details.
 *
 * This list must not be changed within a major IDE release because it breaks "Machine Learning-Assisted Completion"
 * model. The "change" is understood as adding or removing weighers, or changing their ID.
 */
val RS_COMPLETION_WEIGHERS: List<Any> = listOf(
    /**
     * Based on the value passed via [com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority].
     * Unused in our case.
     * @see com.intellij.codeInsight.completion.PriorityWeigher
     */
    "priority",

    // HINT: jump to `P::isFullLineCompletion` docs
    preferTrue(P::isFullLineCompletion, id = "rust-impl-member-full-line-completion"),
    preferUpperVariant(P::keywordKind, id = "rust-prefer-keywords"),
    preferTrue(P::isSelfTypeCompatible, id = "rust-prefer-compatible-self-type"),
    preferTrue(P::isReturnTypeConformsToExpectedType, id = "rust-prefer-matching-expected-type"),
    // TODO these weighers most likely should be after "stats"
    preferTrue(P::isLocal, id = "rust-prefer-locals"),
    preferUpperVariant(P::elementKind, id = "rust-prefer-by-kind"),
    preferTrue(P::isInherentImplMember, id = "rust-prefer-inherent-impl-member"),

    /**
     * Checks prefix matching.
     * @see com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
     */
    "prefix",

    /**
     * Bubbles up the most frequently used items.
     * @see com.intellij.codeInsight.completion.StatisticsWeigher.LookupStatisticsWeigher
     */
    "stats",

    /**
     * Puts closer elements above more distant ones (relative to the location where completion is invoked).
     * For example, elements from workspace packages considered closer than elements from from external packages.
     * Specific rules are defined by [com.intellij.psi.util.proximity.ProximityWeigher] implementations
     * registered using `<com.intellij.weigher key="proximity">` extension point.
     * @see com.intellij.codeInsight.completion.LookupElementProximityWeigher
     */
    "proximity",
)

private typealias P = RsLookupElementProperties

private fun preferTrue(
    property: (P) -> Boolean,
    id: String
): RsCompletionWeigher = object : RsCompletionWeigher {
    override fun weigh(element: LookupElement): Boolean =
        if (element is RsLookupElement) !property(element.props) else true

    override val id: String get() = id
}

private fun preferUpperVariant(
    property: (P) -> Enum<*>,
    id: String
): RsCompletionWeigher = object : RsCompletionWeigher {
    override fun weigh(element: LookupElement): Int =
        if (element is RsLookupElement) property(element.props).ordinal else Int.MAX_VALUE

    override val id: String get() = id
}
