/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil

class StdOutputCollectingProcessListener : ProcessAdapter() {
    private val stdoutBuffer: StringBuilder = StringBuilder()
    private val stderrBuffer: StringBuilder = StringBuilder()
    private var storedLength: Int = 0

    val stdout: CharSequence get() = stdoutBuffer
    val stderr: CharSequence get() = stderrBuffer

    @Synchronized
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        val output = when (outputType) {
            ProcessOutputType.STDOUT -> stdoutBuffer
            ProcessOutputType.STDERR -> stderrBuffer
            else -> return
        }

        if (storedLength > 16384) return

        val text = event.text
        if (StringUtil.isEmptyOrSpaces(text)) return
        storedLength += text.length

        output.append(text)
    }
}
