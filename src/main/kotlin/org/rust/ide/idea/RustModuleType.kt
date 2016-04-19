package org.rust.ide.idea

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import org.rust.ide.icons.RustIcons
import javax.swing.Icon

class RustModuleType : ModuleType<RustModuleBuilder>(ID) {
    override fun getNodeIcon(isOpened: Boolean): Icon = RustIcons.RUST

    override fun createModuleBuilder(): RustModuleBuilder = RustModuleBuilder()

    override fun getDescription(): String = "Rust module"

    override fun getName(): String = "Rust"

    override fun getBigIcon(): Icon = RustIcons.RUST_BIG

    companion object {
        private val ID = "RUST_MODULE"
        val INSTANCE: RustModuleType by lazy { ModuleTypeManager.getInstance().findByID(ID) as RustModuleType }
    }
}
