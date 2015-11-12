package org.rust.lang.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import org.rust.lang.icons.RustIcons

private val NEW_RUST_FILE = "New Rust File"

class RustCreateFileAction : CreateFileFromTemplateAction(NEW_RUST_FILE, "", RustIcons.FILE)
                           , DumbAware {

    override fun getActionName(directory: PsiDirectory?, newName: String?, templateName: String?): String {
        return NEW_RUST_FILE
    }

    override fun buildDialog(project: Project?, directory: PsiDirectory?,
                             builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle(NEW_RUST_FILE)
                .addKind("Empty File", RustIcons.FILE, "Rust File")
    }
}