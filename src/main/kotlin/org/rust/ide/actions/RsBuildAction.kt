/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
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
        private fun isSuitablePlatform(): Boolean = !(PlatformUtils.isIntelliJ() || PlatformUtils.isAppCode())
    }
}
