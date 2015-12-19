package org.rust.cargo.project.module

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleTypeManager
import org.rust.lang.icons.RustIcons
import javax.swing.Icon

class RustLibraryModuleType : RustModuleType(RustLibraryModuleType.MODULE_TYPE_ID) {

    override fun createModuleBuilder(): RustModuleBuilder = RustModuleBuilder(INSTANCE)

    override fun getName(): String          = "Rust Library Module"
    override fun getDescription(): String   = "Rust Library Module"

    override fun getBigIcon(): Icon = RustIcons.RUST
    override fun getNodeIcon(isOpened: Boolean): Icon = AllIcons.Nodes.Module

    companion object {
        val MODULE_TYPE_ID = "RUST_LIBRARY_MODULE"
        val INSTANCE by lazy {
            ModuleTypeManager.getInstance().findByID(MODULE_TYPE_ID) as RustModuleType
        }
    }

}


