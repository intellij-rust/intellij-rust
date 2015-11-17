package org.rust.lang.module

import com.intellij.openapi.module.ModuleType
import org.rust.lang.icons.RustIcons
import javax.swing.Icon


class RustModuleType : ModuleType<RustModuleBuilder>("RUST_MODULE") {

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
        val INSTANCE = RustModuleType()
    }
}
