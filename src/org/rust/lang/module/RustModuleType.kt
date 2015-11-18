package org.rust.lang.module

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import org.rust.lang.icons.RustIcons
import javax.swing.Icon


class RustModuleType : ModuleType<RustModuleBuilder>(RustModuleType.MODULE_TYPE_ID) {

    override fun createModuleBuilder(): RustModuleBuilder {
        return RustModuleBuilder()
    }

    override fun getName(): String {
        return "Rust Module"
    }

    override fun getDescription(): String {
        return "Rust Module"
    }

    override fun getBigIcon(): Icon {
        return RustIcons.FILE_BIG
    }

    override fun getNodeIcon(isOpened: Boolean): Icon {
        return RustIcons.FILE
    }

    companion object {
        val MODULE_TYPE_ID = "RUST_MODULE"
        val INSTANCE: RustModuleType = ModuleTypeManager.getInstance().findByID(MODULE_TYPE_ID) as RustModuleType
    }
}
