package org.rust.cargo.project

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.CargoConstants
import org.rust.cargo.icons.CargoIcons
import javax.swing.Icon

class CargoProjectImportProvider(builder: CargoProjectImportBuilder)
        : AbstractExternalProjectImportProvider(builder, CargoProjectSystem.ID) {

    override fun getId(): String = CargoConstants.ID

    override fun getName(): String = CargoConstants.NAME

    override fun getIcon(): Icon = CargoIcons.ICON

    override fun canImport(entry: VirtualFile, project: Project?): Boolean = canImport(entry)

    override fun getPathToBeImported(file: VirtualFile): String =
        if (file.isDirectory) file.path else file.parent.path

    override fun getFileSample(): String = "<b>Cargo</b> project file (Cargo.toml)"

    companion object {
        fun canImport(entry0: VirtualFile?): Boolean {
            var entry = entry0
            if (entry != null && entry.isDirectory) {
                entry = entry.findChild(CargoConstants.MANIFEST_FILE)
            }
            return entry != null && !entry.isDirectory && CargoConstants.MANIFEST_FILE == entry.name
        }
    }
}
