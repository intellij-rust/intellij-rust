package org.rust.lang.core.resolve

import com.intellij.openapi.module.ModuleType
import org.rust.cargo.project.module.RustExecutableModuleType
import org.rust.cargo.project.module.RustLibraryModuleType

class RustMultiFileResolveTestCaseLibraryTarget : RustMultiFileResolveTestCaseBase() {

    override val moduleType: ModuleType<*>
        get() = RustLibraryModuleType.INSTANCE

    fun testGlobalPath()    = doTest("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
    fun testUseViewPath()   = doTest("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
}
