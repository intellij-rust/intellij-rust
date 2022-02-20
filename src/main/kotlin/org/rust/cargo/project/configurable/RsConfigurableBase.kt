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
import org.rust.cargo.project.settings.RustProjectSettingsService.State
import org.rust.cargo.project.settings.rustSettings
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties

@Suppress("UnstableApiUsage")
abstract class RsConfigurableBase(
    protected val project: Project,
    @ConfigurableName displayName: String
) : BoundConfigurable(displayName) {
    protected val state: State = project.rustSettings.settingsState
    private val oldState: State = state.copy()

    // Currently, we have help page only for CLion
    override fun getHelpTopic(): String? = if (PlatformUtils.isCLion()) "rustsupport" else null

    @Throws(ConfigurationException::class)
    final override fun apply() {
        super.apply()
        doApply()
        project.rustSettings.settingsState = getUpdatedState()
    }

    private fun getUpdatedState(): State {
        val currentState = project.rustSettings.settingsState
        for (property in stateProperties) {
            val newValue = property.get(state)
            val oldValue = property.get(oldState)
            if (newValue != oldValue) {
                property.set(currentState, newValue)
            }
        }
        return currentState
    }

    @Throws(ConfigurationException::class)
    protected open fun doApply() {
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private val stateProperties = State::class.declaredMemberProperties
            .filterIsInstance<KMutableProperty1<State, *>>()
            as List<KMutableProperty1<State, Any?>>
    }
}
