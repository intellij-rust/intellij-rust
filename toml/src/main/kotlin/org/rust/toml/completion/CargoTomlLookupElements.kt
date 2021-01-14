/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.toml.StringLiteralInsertionHandler
import org.toml.lang.psi.TomlKeySegment

fun lookupElementForFeature(feature: TomlKeySegment): LookupElementBuilder {
    return LookupElementBuilder
        .createWithSmartPointer(feature.text, feature)
        .withInsertHandler(StringLiteralInsertionHandler())
}
