/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.rust.lang.core.or
import org.rust.toml.CargoTomlPsiPattern.onDependencyKey
import org.rust.toml.CargoTomlPsiPattern.onSpecificDependencyHeaderKey
import org.rust.toml.tomlPluginIsAbiCompatible

class CargoTomlReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        if (tomlPluginIsAbiCompatible()) {
            registrar.registerReferenceProvider(
                onDependencyKey or onSpecificDependencyHeaderKey,
                CargoDependencyReferenceProvider()
            )
            for (type in PathPatternType.values()) {
                registrar.registerReferenceProvider(type.pattern, CargoTomlFileReferenceProvider(type))
            }
        }
    }
}
