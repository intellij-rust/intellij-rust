/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.psi.shouldIndexFile

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class TestAllStdlibFilesAreIndexed : RsTestBase() {
    fun `test all stdlib files are indexed`() {
        InlineFile("")
        val crateIds = project.crateGraph.topSortedCrates.mapNotNull { it.id }
        val defMaps = project.defMapService.getOrUpdateIfNeeded(crateIds).values.filterNotNull()
        val usedFiles = defMaps
            .flatMap { it.fileInfos.keys }
            .mapNotNull { PersistentFS.getInstance().findFileById(it) }
        val notIndexedFiles = usedFiles.filter { !shouldIndexFile(project, it) }

        if (notIndexedFiles.isNotEmpty()) {
            val filePaths = notIndexedFiles.joinToString(separator = "\n") { "  ${it.path}" }
            error(
                "The following files are not indexed but are included to the module tree.\n" +
                    "If the files are parts of stdlib, they must be added to `additionalRoots()`.\n" +
                    "Files:\n" +
                    filePaths
            )
        }
    }
}
