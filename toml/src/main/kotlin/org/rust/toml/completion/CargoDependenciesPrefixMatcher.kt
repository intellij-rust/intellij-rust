/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil

class CargoDependenciesPrefixMatcher(prefix: String): PrefixMatcher(prefix) {
    private val normalizedPrefix: String = normalize(prefix)
    private val minusculeMatcher: MinusculeMatcher =
        NameUtil.buildMatcher(normalizedPrefix).withSeparators("_").build()

    override fun prefixMatches(name: String): Boolean {
        val normalizedName = normalize(name)
        return minusculeMatcher.matches(normalizedName)
    }

    override fun cloneWithPrefix(prefix: String): PrefixMatcher =
        CargoDependenciesPrefixMatcher(prefix)

    private fun normalize(string: String) =
        string.replace('-', '_')
}
