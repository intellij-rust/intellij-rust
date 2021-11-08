/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import com.intellij.util.PlatformUtils
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings

@Suppress("UnstableApiUsage")
abstract class RsConfigurableBase(
    protected val project: Project,
    @ConfigurableName displayName: String
) : BoundConfigurable(displayName) {

    protected val state: RustProjectSettingsService.State = project.rustSettings.settingsState

    // Currently, we have help page only for CLion
    override fun getHelpTopic(): String? = if (PlatformUtils.isCLion()) "rustsupport" else null

    @Throws(ConfigurationException::class)
    final override fun apply() {
        super.apply()
        doApply()
        project.rustSettings.settingsState = state
    }

    @Throws(ConfigurationException::class)
    protected open fun doApply() {}
}
