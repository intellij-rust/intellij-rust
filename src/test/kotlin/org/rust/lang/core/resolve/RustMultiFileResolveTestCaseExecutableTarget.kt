package org.rust.lang.core.resolve

import com.intellij.openapi.module.ModuleType
import org.rust.cargo.project.module.RustExecutableModuleType

class RustMultiFileResolveTestCaseExecutableTarget : RustMultiFileResolveTestCaseBase() {

    override val moduleType: ModuleType<*>
        get() = RustExecutableModuleType.INSTANCE

    fun testChildMod()          = doTest("child_mod/main.rs", "child_mod/child.rs")
    fun testNestedChildMod()    = doTest("nested_child_mod/main.rs", "nested_child_mod/inner/child.rs")
    fun testModDecl()           = doTest("mod_decl/main.rs", "mod_decl/foo.rs")
}
