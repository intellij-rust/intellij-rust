/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.cidr.execution.CidrDebuggerBundle
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointType
import org.rust.lang.RsFileType

class RsLineBreakpointType : CidrLineBreakpointType(
    "org.rust.debugger.RsLineBreakpointType",
    CidrDebuggerBundle.message("debug.breakpoint.line")
) {
    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean =
        file.fileType == RsFileType
}
