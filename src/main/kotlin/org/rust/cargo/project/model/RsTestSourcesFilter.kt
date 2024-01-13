/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.isAncestor
import org.rust.ide.injected.isDoctestInjection
import org.rust.openapiext.modules
import org.rust.openapiext.pathAsPath

class RsTestSourcesFilter : TestSourcesFilter() {
    override fun isTestSource(file: VirtualFile, project: Project): Boolean {
        val isInTestDir = getTestSourceFolders(project).any { it.file?.pathAsPath?.isAncestor(file.pathAsPath) == true }
        return isInTestDir || file.isDoctestInjection(project)
    }
}

fun getTestSourceFolders(project: Project): Sequence<SourceFolder> {
    return project.modules
        .asSequence()
        .flatMap { module ->
            ModuleRootManager.getInstance(module).contentEntries
                .asSequence()
                .flatMap { contentEntry ->
                    contentEntry.sourceFolders.filter { it.isTestSource }
                }
        }
}
