/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.util.io.BaseOutputReader
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok

class RsCapturingProcessHandler private constructor(commandLine: GeneralCommandLine) : CapturingProcessHandler(commandLine) {
    override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING

    companion object {
        fun startProcess(commandLine: GeneralCommandLine): RsResult<RsCapturingProcessHandler, ExecutionException> {
            return try {
                Ok(RsCapturingProcessHandler(commandLine))
            } catch (e: ExecutionException) {
                Err(e)
            }
        }
    }
}
