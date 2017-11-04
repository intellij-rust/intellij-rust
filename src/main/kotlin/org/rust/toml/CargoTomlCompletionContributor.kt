/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.Project

class CargoTomlCompletionContributor : CompletionContributor() {
    init {
        val pattern = try {
            CargoTomlKeysCompletionProvider.getElementPattern()
        } catch (e: LinkageError) {
            null // Incompatible version of TOML plugin
        }
        if (pattern != null) {
            extend(CompletionType.BASIC, pattern, CargoTomlKeysCompletionProvider())
        }
    }
}
