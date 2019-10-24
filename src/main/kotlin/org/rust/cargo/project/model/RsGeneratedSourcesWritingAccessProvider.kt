/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider

class RsGeneratedSourcesWritingAccessProvider(private val project: Project) : WritingAccessProvider() {
    override fun requestWriting(vararg files: VirtualFile): Collection<VirtualFile> {
        val cargoProjects = project.cargoProjects
        return files.filter { cargoProjects.isGeneratedFile(it) }
    }

    override fun isPotentiallyWritable(file: VirtualFile): Boolean {
        return !project.cargoProjects.isGeneratedFile(file)
    }
}
