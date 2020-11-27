/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.rust.lang.core.RsPsiPattern
import org.rust.toml.tomlPluginIsAbiCompatible

/** Provides references (that point to TOML elements) for Rust elements in Rust files */
class RsCargoTomlIntegrationReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        if (tomlPluginIsAbiCompatible()) {
            registrar.registerReferenceProvider(RsPsiPattern.anyCfgFeature, RsCfgFeatureReferenceProvider())
        }
    }
}
