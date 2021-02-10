/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.search

import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.rust.lang.core.RsPsiPattern.anyCfgFeature
import org.rust.lang.core.psi.RsLitExpr
import org.rust.toml.CargoTomlPsiPattern.onFeatureDependencyLiteral
import org.rust.toml.tomlPluginIsAbiCompatible
import org.toml.lang.psi.TomlLiteral

class CargoTomlUsageTypeProvider : UsageTypeProviderEx {
    override fun getUsageType(element: PsiElement): UsageType? = getUsageType(element, UsageTarget.EMPTY_ARRAY)

    override fun getUsageType(element: PsiElement?, targets: Array<out UsageTarget>): UsageType? {
        if (!tomlPluginIsAbiCompatible()) return null
        return when (element) {
            is TomlLiteral -> if (onFeatureDependencyLiteral.accepts(element)) FEATURE_DEPENDENCY else DEPENDENCY_FEATURE
            is RsLitExpr -> if (anyCfgFeature.accepts(element)) CFG_FEATURE else null
            else -> null
        }
    }

    companion object {
        private val FEATURE_DEPENDENCY = UsageType { "Cargo feature dependency" }
        private val DEPENDENCY_FEATURE = UsageType { "Package dependency" }
        private val CFG_FEATURE = UsageType { "Cfg attribute" }
    }
}
