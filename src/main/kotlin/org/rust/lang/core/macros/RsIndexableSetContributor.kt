/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor

class RsIndexableSetContributor : IndexableSetContributor() {
    override fun getAdditionalRootsToIndex(): Set<VirtualFile> = emptySet()

    override fun getAdditionalProjectRootsToIndex(project: Project): Set<VirtualFile> =
        listOfNotNull(project.macroExpansionManager.indexableDirectory).toSet()
}
