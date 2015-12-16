package org.rust.cargo.project.module

import com.intellij.openapi.module.ModuleType

/**
 * Abstract Rust's module-type (a-la Crate)
 */
abstract class RustModuleType(typeId: String) : ModuleType<RustModuleBuilder>(typeId)
