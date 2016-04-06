package org.rust.cargo.toolchain

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

interface RustProjectSettingsService {
    var toolchain: RustToolchain?
}

val Project.toolchain: RustToolchain? get() = service<RustProjectSettingsService>().toolchain
val Module.toolchain: RustToolchain? get() = project.toolchain
