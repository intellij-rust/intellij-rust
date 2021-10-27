/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.indexing

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.macros.macroExpansionManagerIfCreated

class RsIndexableSetContributor : IndexableSetContributor() {
    override fun getAdditionalRootsToIndex(): Set<VirtualFile> = emptySet()

    override fun getAdditionalProjectRootsToIndex(project: Project): Set<VirtualFile> {
        val additionalProjectRootsToIndex = hashSetOf<VirtualFile>()

        project.cargoProjects.allProjects.forEach { cargoProject ->
            cargoProject.workspace?.packages.orEmpty().mapNotNullTo(additionalProjectRootsToIndex) { it.outDir }
        }
        project.macroExpansionManagerIfCreated?.indexableDirectory?.let {
            additionalProjectRootsToIndex += it
        }

        return additionalProjectRootsToIndex
    }
}
