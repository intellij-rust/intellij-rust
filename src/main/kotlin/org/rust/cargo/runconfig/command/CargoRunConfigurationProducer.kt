/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

abstract class CargoRunConfigurationProducer : LazyRunConfigurationProducer<CargoCommandConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return CargoCommandConfigurationType.getInstance().factory
    }

    public abstract override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean
}
