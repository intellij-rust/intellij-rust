/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ml

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.lang.Language
import org.rust.RsBundle
import org.rust.lang.RsLanguage

@Suppress("UnstableApiUsage")
class RsMLRankingProvider : CatBoostJarCompletionModelProvider(RsBundle.message("rust"), "rust_features", "rust_model") {
    override fun isLanguageSupported(language: Language): Boolean = language == RsLanguage
    override fun getDecoratingPolicy(): DecoratingItemsPolicy = DecoratingItemsPolicy.Composite(
        DecoratingItemsPolicy.ByAbsoluteThreshold(3.0),
        DecoratingItemsPolicy.ByRelativeThreshold(2.5)
    )

    override fun isEnabledByDefault(): Boolean = true
}
