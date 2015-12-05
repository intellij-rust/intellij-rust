package org.rust.cargo.project

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.Cargo

class CargoOpenProjectDescriptor : OpenProjectFileChooserDescriptor(true) {

    override fun isFileVisible(file: VirtualFile, showHiddenFiles: Boolean): Boolean {
        return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory || Cargo.BUILD_FILE == file.name)
    }

    override fun isFileSelectable(file: VirtualFile): Boolean {
        return super.isFileSelectable(file) && CargoProjectImportProvider.canImport(file)
    }
}
