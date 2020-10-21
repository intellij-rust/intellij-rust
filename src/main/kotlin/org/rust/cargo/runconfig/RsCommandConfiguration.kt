/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jdom.Element
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RsToolchain
import org.rust.ide.sdk.RsSdkUtils
import org.rust.ide.sdk.key
import org.rust.ide.sdk.toolchain

abstract class RsCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction {
    var sdkKey: String? = null

    var sdk: Sdk?
        get() = sdkKey?.let { RsSdkUtils.findSdkByKey(it) }
        set(value) {
            sdkKey = value?.key
        }

    val toolchain: RsToolchain?
        get() = sdk?.toolchain ?: project.toolchain

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("sdkKey", sdkKey)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readString("sdkKey")?.let { sdkKey = it }
    }
}
