package org.rust.cargo.projectSettings

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.rust.cargo.toolchain.RustToolchain

interface RustProjectSettingsService {
    var toolchain: RustToolchain?
    var autoUpdateEnabled: Boolean
}

val Project.rustSettings: RustProjectSettingsService get() = service()

val Module.toolchain: RustToolchain? get() = project.rustSettings.toolchain
