/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion.sort

import com.intellij.codeInsight.lookup.LookupElement

/**
 * A weigher for Rust completion variants.
 *
 * @see RS_COMPLETION_WEIGHERS
 */
interface RsCompletionWeigher {
    /**
     * Returned values are sorted in ascending order, i.e.
     * - `0`, `1`, `2`,
     * - `false`, `true`,
     * - upper enum variants before bottom ones.
     *
     * Note that any (Boolean, Number or Enum) value returned from [weigh] is automatically added
     * as an "element feature" in ML-Assisted Completion (identified by [id]), see
     * [org.rust.ml.RsElementFeatureProvider] for more details.
     */
    fun weigh(element: LookupElement): Comparable<*>

    /**
     * The [id] turns into [com.intellij.codeInsight.lookup.LookupElementWeigher.myId], which then turns into
     * [com.intellij.codeInsight.lookup.ClassifierFactory.getId] which is used for comparison of
     * [com.intellij.codeInsight.completion.CompletionSorter]s.
     *
     * Also, [id] identifies the value returned from [weigh] when the value is used as an "element feature"
     * in ML-Assisted Completion (see [org.rust.ml.RsElementFeatureProvider]).
     */
    val id: String
}
