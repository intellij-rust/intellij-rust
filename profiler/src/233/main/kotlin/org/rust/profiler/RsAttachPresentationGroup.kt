/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler

import com.intellij.execution.process.ProcessInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.xdebugger.attach.XAttachProcessPresentationGroup
import org.rust.RsBundle
import javax.swing.Icon

object RsAttachPresentationGroup: XAttachProcessPresentationGroup {
    override fun getOrder(): Int = 0

    override fun getGroupName(): String = RsBundle.message("profiler.attach.default.group.title")

    override fun getItemIcon(project: Project, info: ProcessInfo, dataHolder: UserDataHolder): Icon {
        return AllIcons.RunConfigurations.Application
    }

    override fun getItemDisplayText(project: Project, info: ProcessInfo, dataHolder: UserDataHolder): String {
        return info.executableDisplayName
    }
}
