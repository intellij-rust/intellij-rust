/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.PrefixMatcher

class CargoNormalizedNamesPrefixMatcher(prefix: String): PrefixMatcher(prefix) {
    override fun prefixMatches(name: String): Boolean =
        name.replace('-', '_').startsWith(prefix.replace('-', '_'))

    override fun cloneWithPrefix(prefix: String): PrefixMatcher =
        CargoNormalizedNamesPrefixMatcher(prefix)
}
