/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.rust.lang.core.or
import org.rust.toml.CargoTomlPsiPattern.onDependencyKey
import org.rust.toml.CargoTomlPsiPattern.onDependencyPackageFeature
import org.rust.toml.CargoTomlPsiPattern.dependencyGitUrl
import org.rust.toml.CargoTomlPsiPattern.onFeatureDependencyLiteral
import org.rust.toml.CargoTomlPsiPattern.packageUrl
import org.rust.toml.CargoTomlPsiPattern.onSpecificDependencyHeaderKey
import org.rust.toml.tomlPluginIsAbiCompatible

/** Provides references for TOML elements in `Cargo.toml` files */
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
            registrar.registerReferenceProvider(onFeatureDependencyLiteral, CargoTomlFeatureDependencyReferenceProvider())
            registrar.registerReferenceProvider(onDependencyPackageFeature, CargoTomlDependencyFeaturesReferenceProvider())
            // Starting from 2022.2 Toml plugin inserts web references in all string literals itself if needed
            if (ApplicationInfo.getInstance().build < BUILD_222) {
                registrar.registerReferenceProvider(dependencyGitUrl or packageUrl, CargoTomlUrlReferenceProvider())
            }
        }
    }

    companion object {
        // BACKCOMPAT: 2022.1
        private val BUILD_222 = BuildNumber.fromString("222")!!
    }
}
