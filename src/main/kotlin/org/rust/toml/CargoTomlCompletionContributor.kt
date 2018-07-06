/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType.BASIC
import org.rust.toml.CargoTomlPsiPattern.inDependencyKeyValue
import org.rust.toml.CargoTomlPsiPattern.inKey
import org.rust.toml.CargoTomlPsiPattern.inSpecificDependencyHeaderKey
import org.rust.toml.CargoTomlPsiPattern.inSpecificDependencyKeyValue

class CargoTomlCompletionContributor : CompletionContributor() {
    init {
        if (tomlPluginIsAbiCompatible()) {
            extend(BASIC, inKey, CargoTomlKeysCompletionProvider())
            extend(BASIC, inDependencyKeyValue, CargoTomlDependencyCompletionProvider())
            extend(BASIC, inSpecificDependencyHeaderKey, CargoTomlSpecificDependencyHeaderCompletionProvider())
            extend(BASIC, inSpecificDependencyKeyValue, CargoTomlSpecificDependencyVersionCompletionProvider())
        }
    }
}
