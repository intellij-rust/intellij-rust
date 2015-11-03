package org.rust.cargo.project

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.Cargo
import javax.swing.Icon

class CargoProjectImportProvider(builder: CargoProjectImportBuilder) :
        AbstractExternalProjectImportProvider(builder, CargoProjectSystem.ID) {

    override fun getId(): String {
        return Cargo.ID
    }

    override fun getName(): String {
        return Cargo.NAME
    }

    override fun getIcon(): Icon {
        return Cargo.ICON
    }

    override fun canImport(entry: VirtualFile, project: Project?): Boolean {
        return canImport(entry)
    }

    override fun getPathToBeImported(file: VirtualFile): String {
        return if (file.isDirectory) file.path else file.parent.path
    }

    override fun getFileSample(): String {
        return "<b>Cargo</b> project file (Cargo.toml)"
    }

    companion object {
        fun canImport(entry0: VirtualFile?): Boolean {
            var entry = entry0
            if (entry != null && entry.isDirectory) {
                entry = entry.findChild(Cargo.BUILD_FILE)
            }
            return entry != null && !entry.isDirectory && Cargo.BUILD_FILE == entry.name
        }
    }
}
