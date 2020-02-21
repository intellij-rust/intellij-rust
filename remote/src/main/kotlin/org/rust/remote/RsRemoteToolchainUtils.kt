/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.remote

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.remote.RemoteSdkException
import com.intellij.util.ui.UIUtil
import org.rust.cargo.runconfig.remote.RsRemoteProcessStarterManager
import org.rust.ide.sdk.remote.RsRemotePathMapper
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalDataBase
import org.rust.stdext.RsExecutionException

object RsRemoteToolchainUtils {

    fun getToolchainVersion(
        project: Project?,
        data: RsRemoteSdkAdditionalDataBase,
        nullForUnparsableVersion: Boolean
    ): String? {
        val result = Ref.create<String>(null)
        val exception = Ref.create<RemoteSdkException>(null)
        val task: Task.Modal = object : Task.Modal(project, "Getting Remote Toolchain Version", true) {
            override fun run(indicator: ProgressIndicator) {
                val flavor = data.flavor
                if (flavor != null) {
                    try {
                        val command = arrayOf<String>(data.interpreterPath, flavor.getVersionOption())
                        val processOutput = RsRemoteProcessStarterManager.getManager(data)
                            .executeRemoteProcess(myProject, command, null, data, RsRemotePathMapper())
                        if (processOutput.exitCode == 0) {
                            val version = flavor.getVersionStringFromOutput(processOutput)
                            if (version != null || nullForUnparsableVersion) {
                                result.set(version)
                                return
                            }
                        }
                        exception.set(createException(processOutput, command))
                    } catch (e: Exception) {
                        exception.set(RemoteSdkException.cantObtainRemoteCredentials(e))
                    }
                }
            }
        }

        if (!ProgressManager.getInstance().hasProgressIndicator()) {
            UIUtil.invokeAndWaitIfNeeded(Runnable { ProgressManager.getInstance().run(task) })
        } else {
            task.run(ProgressManager.getInstance().progressIndicator)
        }

        if (!exception.isNull) {
            throw exception.get()
        }

        return result.get()
    }

    private fun createException(processOutput: ProcessOutput, command: Array<String>): RemoteSdkException {
        val exception = RsExecutionException("Can't obtain Rust version", command.first(), arrayListOf(*command), processOutput)
        return RemoteSdkException.cantObtainRemoteCredentials(exception)
    }
}
