/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.psi.stubs.SerializedStubTreeDataExternalizer
import com.intellij.psi.stubs.StubForwardIndexExternalizer
import com.intellij.testFramework.ReadOnlyLightVirtualFile
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileContentImpl

@Suppress("UnstableApiUsage")
fun newSerializedStubTreeDataExternalizer(
    manager: SerializationManagerEx,
    externalizer: StubForwardIndexExternalizer<*>,
): SerializedStubTreeDataExternalizer {
    return SerializedStubTreeDataExternalizer(
        /* includeInputs = */ true,
        manager,
        externalizer
    )
}

@Suppress("UnstableApiUsage")
fun createFileContent(project: Project, file: ReadOnlyLightVirtualFile, fileContent: String): FileContent =
    FileContentImpl(file, fileContent, file.modificationStamp).also { it.project = project }

fun createFileAttributes(isDir: Boolean, length: Long, lastModified: Long): FileAttributes =
    FileAttributes(isDir, false, false, false, length, lastModified, true)
