/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.config.CheckingContext
import org.rust.lang.RsLanguage

fun CheckingContext.withRsLanguageEnabled(): CheckingContext = copy(enabledLanguages = enabledLanguages + RsLanguage.id)
