/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.ui.JBUI
import org.rust.ide.newProject.RsDirectoryProjectGenerator
import org.rust.ide.newProject.RsProjectSettingsStep
import javax.swing.JComponent
import javax.swing.JPanel

// BACKCOMPAT: 2017.3
// Temporary action to create new Rust project in CLion (but works in all IDEs)
class RsNewProjectAction : RsProjectSettingsStep(RsDirectoryProjectGenerator(true)) {

    override fun actionPerformed(e: AnActionEvent) {
        val panel = createPanel()
        panel.preferredSize = JBUI.size(600, 300)
        RsNewProjectDialog(panel).show()
    }

    override fun update(e: AnActionEvent) {
        val build = ApplicationInfo.getInstance().build
        e.presentation.isEnabledAndVisible = build < BUILD_181
    }

    companion object {
        private val BUILD_181 = BuildNumber.fromString("181")
    }
}

private class RsNewProjectDialog(private val centerPanel: JPanel) : DialogWrapper(true) {

    init {
        title = "New Project"
        init()
    }

    override fun createCenterPanel(): JComponent? = centerPanel
    override fun createSouthPanel(): JComponent? = null
}
