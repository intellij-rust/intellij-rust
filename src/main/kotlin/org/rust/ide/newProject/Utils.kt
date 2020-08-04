/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isHeadlessEnvironment
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.Cargo.Companion.GeneratedFilesHolder

fun Project.openFiles(files: GeneratedFilesHolder) = invokeLater {
    if (!isHeadlessEnvironment) {
        val navigation = PsiNavigationSupport.getInstance()
        navigation.createNavigatable(this, files.manifest, -1).navigate(false)
        for (file in files.sourceFiles) {
            navigation.createNavigatable(this, file, -1).navigate(true)
        }
    }
}

fun Cargo.makeProject(
    project: Project,
    module: Module,
    baseDir: VirtualFile,
    template: RsProjectTemplate
): GeneratedFilesHolder? = when (template) {
    is RsGenericTemplate -> init(project, module, baseDir, template.isBinary)
    is RsCustomTemplate -> generate(project, module, baseDir, template.link)
}
