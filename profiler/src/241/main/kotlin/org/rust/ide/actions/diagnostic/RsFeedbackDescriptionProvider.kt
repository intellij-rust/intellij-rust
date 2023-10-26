/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.diagnostic

import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils
import org.rust.cargo.runconfig.hasCargoProject

class RsFeedbackDescriptionProvider : RsFeedbackDescriptionProviderBase() {
    override suspend fun getDescription(project: Project?): String? {
        return if (project != null && (PlatformUtils.getPlatformPrefix() == "RustRover" || project.hasCargoProject)) {
            getEnvironmentInfo(project)
        } else {
            null
        }
    }
}
