package org.rust.cargo.project

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CargoConstants

object  CargoOpenProjectDescriptor : OpenProjectFileChooserDescriptor(true) {

    override fun isFileVisible(file: VirtualFile, showHiddenFiles: Boolean): Boolean =
        super.isFileVisible(file, showHiddenFiles) && (file.isDirectory || CargoConstants.MANIFEST_FILE == file.name)

    override fun isFileSelectable(file: VirtualFile): Boolean =
        super.isFileSelectable(file) && CargoProjectImportProvider.canImport(file)
}
