package org.rust.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool

abstract class RustLocalInspectionTool : LocalInspectionTool() {
    final override fun getGroupDisplayName(): String = "Rust"
}
