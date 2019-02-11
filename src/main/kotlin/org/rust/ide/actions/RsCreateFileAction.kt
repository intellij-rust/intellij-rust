/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.icons.RsIcons


class RsCreateFileAction : CreateFileFromTemplateAction(RsCreateFileAction.CAPTION, "", RsIcons.RUST_FILE),
                           DumbAware {

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String = CAPTION

    override fun isAvailable(dataContext: DataContext): Boolean {
        if (!super.isAvailable(dataContext)) return false
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return false
        return project.cargoProjects.allProjects.any {
            val rootDir = it.rootDir ?: return@any false
            VfsUtil.isAncestor(rootDir, vFile, false)
        }
    }

    override fun buildDialog(project: Project?, directory: PsiDirectory?,
                             builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle(CAPTION)
            .addKind("Empty File", RsIcons.RUST_FILE, "Rust File")
    }

    private companion object {
        private const val CAPTION = "New Rust File"
    }
}
