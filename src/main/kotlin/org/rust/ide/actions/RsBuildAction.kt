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
        private val BUILD_191 = BuildNumber.fromString("191")

        private fun isSuitablePlatform(): Boolean {
            val buildNumber = ApplicationInfo.getInstance().build
            // BACKCOMPAT: 2018.3
            //  CLion supports project task api since 2019.1
            return !(PlatformUtils.isIntelliJ() || PlatformUtils.isAppCode() ||
                PlatformUtils.isCLion() && buildNumber >= BUILD_191)
        }
    }
}
