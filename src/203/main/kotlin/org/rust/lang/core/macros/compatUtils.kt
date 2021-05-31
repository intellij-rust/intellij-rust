/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileContentImpl

fun createFileContentByText(file: VirtualFile, contentAsText: CharSequence, project: Project): FileContent {
    return FileContentImpl.createByText(file, contentAsText).also {
        (it as FileContentImpl).project = project
    }
}
