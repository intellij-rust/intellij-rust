/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.PrefixMatcher
import java.util.*

object CompletionUtilsExt {
    fun sortMatching(prefixMatcher: PrefixMatcher, names: Collection<String>): LinkedHashSet<String> =
        CompletionUtil.sortMatching(prefixMatcher, names)
}
