/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.psi.stubs.SerializedStubTreeDataExternalizer
import com.intellij.psi.stubs.StubForwardIndexExternalizer
import com.intellij.testFramework.ReadOnlyLightVirtualFile
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileContentImpl

// BACKCOMPAT: 2020.2. Inline it
@Suppress("UnstableApiUsage")
fun newSerializedStubTreeDataExternalizer(
    manager: SerializationManagerEx,
    externalizer: StubForwardIndexExternalizer<*>,
): SerializedStubTreeDataExternalizer {
    return SerializedStubTreeDataExternalizer(
        manager,
        externalizer
    )
}

// BACKCOMPAT: 2020.2. Inline it
@Suppress("UnstableApiUsage")
fun createFileContent(project: Project, file: ReadOnlyLightVirtualFile, fileContent: String): FileContent =
    FileContentImpl.createByText(file, fileContent).also { (it as FileContentImpl).project = project }
