/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import org.rust.ide.notifications.setStatusBarText
import org.rust.ide.notifications.showBalloon
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.resolve2.forceRebuildDefMapForAllCrates
import org.rust.lang.core.resolve2.forceRebuildDefMapForCrate
import org.rust.openapiext.psiFile
import kotlin.system.measureTimeMillis

class RsRebuildAllDefMapsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val time = measureTimeMillis {
                project.forceRebuildDefMapForAllCrates(multithread = false)
            }
            project.showBalloon("Rebuilt DefMap for all crates in $time ms", NotificationType.INFORMATION)
        }
    }
}

class RsRebuildCurrentDefMapAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.dataContext.psiFile as? RsFile ?: return
        val crate = file.crate ?: return
        val crateId = crate.id ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val time = measureTimeMillis {
                project.forceRebuildDefMapForCrate(crateId)
            }
            project.setStatusBarText("Rebuilt DefMap for $crate in $time ms")
        }
    }
}
