/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ConfigurableName
import com.intellij.util.PlatformUtils

@Suppress("UnstableApiUsage")
abstract class RsConfigurableBase(
    protected val project: Project,
    @ConfigurableName displayName: String
) : BoundConfigurable(displayName) {
    // Currently, we have help page only for CLion
    override fun getHelpTopic(): String? = if (PlatformUtils.isCLion()) "rustsupport" else null
}
