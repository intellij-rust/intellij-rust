/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.write
import org.apache.commons.lang3.RandomStringUtils
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.ObjectGcTracker
import org.rust.openapiext.pathAsPath
import java.nio.file.Paths

private const val RUST_EXPANDED_MACROS = "rust_expanded_macros"

class MacroExpansionPsiManager : Disposable {
    private val tempDir = Paths.get(PathManager.getTempPath())
        .resolve(RUST_EXPANDED_MACROS)

    init {
        tempDir.delete()
    }

    override fun dispose() {
        tempDir.delete()
    }

    /**
     * Creates a temporary file filled with [content] and loads its PSI.
     * Unlike [com.intellij.psi.PsiFileFactory], it creates stub-based PSI tree
     */
    fun createPsiFromText(project: Project, content: String): RsFile {
        val file = createTempFileWithContent(content)
        val psi = PsiManager.getInstance(project).findFile(file) as RsFile

        // We will delete the file after the psi is GCed
        ObjectGcTracker.instance.registerObjectGcHook(psi) {
            file.pathAsPath.delete()
        }

        return psi
    }

    private fun createTempFileWithContent(content: String): VirtualFile {
        // We creating the file bypass VFS because we don't want to perform write action and fire events
        tempDir.createDirectories()
        val file = tempDir.resolve(RandomStringUtils.randomAlphabetic(16) + ".rs")
        file.write(content)
        return LocalFileSystem.getInstance().findFileByIoFile(file.toFile())!!
    }

    companion object {
        val instance: MacroExpansionPsiManager
            get() = ServiceManager.getService(MacroExpansionPsiManager::class.java)
    }
}
