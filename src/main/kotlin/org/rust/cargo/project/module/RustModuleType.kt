package org.rust.cargo.project.module

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import org.rust.lang.icons.RustIcons
import javax.swing.Icon


class RustModuleType : ModuleType<RustModuleBuilder>(RustModuleType.Companion.MODULE_TYPE_ID) {

    override fun createModuleBuilder(): RustModuleBuilder = RustModuleBuilder()

    override fun getName(): String = "Rust Module"
    override fun getDescription(): String = "Rust Module"

    override fun getBigIcon(): Icon = RustIcons.RUST
    override fun getNodeIcon(isOpened: Boolean): Icon = AllIcons.Nodes.Module

    companion object {
        val MODULE_TYPE_ID = "RUST_MODULE"
        val INSTANCE by lazy {
            ModuleTypeManager.getInstance().findByID(MODULE_TYPE_ID) as RustModuleType
        }
    }
}
