/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import org.rust.lang.RsFileType

class RsProblemFileHighlightFilter : Condition<VirtualFile> {
    override fun value(file: VirtualFile): Boolean =
        file.fileType == RsFileType
}
