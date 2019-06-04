/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType.BASIC
import org.rust.toml.CargoTomlPsiPattern.inBuildKeyValue
import org.rust.toml.CargoTomlPsiPattern.inDependencyKeyValue
import org.rust.toml.CargoTomlPsiPattern.inKey
import org.rust.toml.CargoTomlPsiPattern.inLicenseFileKeyValue
import org.rust.toml.CargoTomlPsiPattern.inSpecificDependencyHeaderKey
import org.rust.toml.CargoTomlPsiPattern.inSpecificDependencyKeyValue
import org.rust.toml.CargoTomlPsiPattern.inValueWithKey
import org.rust.toml.CargoTomlPsiPattern.inWorkspaceKeyValue
import org.rust.toml.CargoTomlPsiPattern.inWorkspaceKeyWithPathValue

class CargoTomlCompletionContributor : CompletionContributor() {
    init {
        if (tomlPluginIsAbiCompatible()) {
            extend(BASIC, inKey, CargoTomlKeysCompletionProvider())
            extend(BASIC, inValueWithKey("edition"), CargoTomlKnownValuesCompletionProvider(listOf("2015", "2018")))
            extend(BASIC, inDependencyKeyValue, CargoTomlDependencyCompletionProvider())
            extend(BASIC, inSpecificDependencyHeaderKey, CargoTomlSpecificDependencyHeaderCompletionProvider())
            extend(BASIC, inSpecificDependencyKeyValue, CargoTomlSpecificDependencyVersionCompletionProvider())
            extend(BASIC, inValueWithKey("path"), CargoTomlPathCompletion.ofDirectories())
            extend(BASIC, inWorkspaceKeyWithPathValue, CargoTomlPathCompletion.ofDirectories())
            extend(BASIC, inWorkspaceKeyValue, CargoTomlPathCompletion.ofDirectories())
            extend(BASIC, inLicenseFileKeyValue, CargoTomlPathCompletion.ofFiles())
            extend(BASIC, inBuildKeyValue, CargoTomlPathCompletion.ofFiles())
        }
    }
}
