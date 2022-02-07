/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ml

import com.intellij.completion.ml.features.CompletionFeaturesPolicy

@Suppress("UnstableApiUsage")
class RsCompletionFeaturesPolicy : CompletionFeaturesPolicy {
    override fun useNgramModel(): Boolean = true
}
