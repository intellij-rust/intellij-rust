/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.remote

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.rust.stdext.Result
import java.util.function.Consumer

object RsUnknownProjectSynchronizer : RsProjectSynchronizer {
    override val defaultRemotePath: String? = null

    override fun getAutoMappings(): Result<PathMappings, String>? = null

    override fun mapFilePath(project: Project, direction: RsSyncDirection, filePath: String): String? = null

    override fun checkSynchronizationAvailable(syncCheckStrategy: RsSyncCheckStrategy): String? =
        "This interpreter type does not support remote project creation"

    override fun syncProject(
        module: Module,
        syncDirection: RsSyncDirection,
        callback: Consumer<Boolean>?,
        vararg fileNames: String
    ) {
        callback?.accept(false)
    }
}
