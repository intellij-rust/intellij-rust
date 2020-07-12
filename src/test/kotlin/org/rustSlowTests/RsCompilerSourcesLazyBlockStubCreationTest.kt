/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.openapi.vfs.VirtualFile
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.stubs.RsLazyBlockStubCreationTestBase

class RsCompilerSourcesLazyBlockStubCreationTest : RsLazyBlockStubCreationTestBase() {
    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun `test stdlib source`() {
        val sources = rustSrcDir()
        checkRustFiles(
            sources,
            ignored = setOf("tests", "test", "doc", "etc", "grammar")
        )
    }

    private fun rustSrcDir(): VirtualFile = projectDescriptor.stdlib!!
}
