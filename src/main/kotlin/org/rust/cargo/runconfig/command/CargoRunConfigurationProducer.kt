/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

// BACKCOMPAT: 2018.3
@Suppress("DEPRECATION")
abstract class CargoRunConfigurationProducer
    : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandConfigurationType.getInstance()) {
    public abstract override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean
}
