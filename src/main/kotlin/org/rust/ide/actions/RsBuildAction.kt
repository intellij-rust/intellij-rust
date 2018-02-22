/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.PlatformUtils
import org.rust.cargo.runconfig.buildProject
import org.rust.cargo.runconfig.hasCargoProject

class RsBuildAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.buildProject()
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = isSuitablePlatform() && e.project?.hasCargoProject == true
    }

    companion object {
        private val BUILD_181 = BuildNumber.fromString("181")

        private fun isSuitablePlatform(): Boolean {
            val buildNumber = ApplicationInfo.getInstance().build
            // BACKCOMPAT: 2017.3
            // Drop version condition for CLion
            return !(PlatformUtils.isIntelliJ() || PlatformUtils.isAppCode() || PlatformUtils.isCLion() && buildNumber < BUILD_181)
        }
    }
}
