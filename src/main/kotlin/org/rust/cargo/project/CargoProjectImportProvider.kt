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

    override fun canImport(fileOrDirectory: VirtualFile, project: Project?): Boolean =
        canImport(fileOrDirectory)

    override fun getPathToBeImported(file: VirtualFile): String =
        if (file.isDirectory) file.path else file.parent.path

    override fun getFileSample(): String = "<b>Cargo</b> project file (Cargo.toml)"

    companion object {
        fun canImport(fileOrDirectory: VirtualFile): Boolean =
            if (fileOrDirectory.isDirectory)
                fileOrDirectory.findChild(CargoConstants.MANIFEST_FILE) != null
            else
                fileOrDirectory.name == CargoConstants.MANIFEST_FILE
    }
}

