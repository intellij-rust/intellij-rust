/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings

abstract class RsConfigurableBase(protected val project: Project) : Configurable {

    protected val settings: RustProjectSettingsService = project.rustSettings

    // Currently, we have help page only for CLion
    override fun getHelpTopic(): String? = if (PlatformUtils.isCLion()) "rustsupport" else null
}
