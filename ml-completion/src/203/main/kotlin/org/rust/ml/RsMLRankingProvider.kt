/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ml

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language
import org.rust.lang.RsLanguage

@Suppress("UnstableApiUsage")
class RsMLRankingProvider : CatBoostJarCompletionModelProvider("Rust", "rust_features", "rust_model") {
    override fun isLanguageSupported(language: Language): Boolean = language == RsLanguage
}
