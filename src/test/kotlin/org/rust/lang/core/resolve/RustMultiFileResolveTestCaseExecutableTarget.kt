package org.rust.lang.core.resolve

import com.intellij.openapi.module.ModuleType
import org.rust.cargo.project.module.RustExecutableModuleType

class RustMultiFileResolveTestCaseExecutableTarget : RustMultiFileResolveTestCaseBase() {

    override val moduleType: ModuleType<*>
        get() = RustExecutableModuleType.INSTANCE

    // Check whether resolving-procedure succeeds

    fun testChildMod()          = doTestResolved("child_mod/main.rs", "child_mod/child.rs")
    fun testNestedChildMod()    = doTestResolved("nested_child_mod/main.rs", "nested_child_mod/inner/child.rs")
    fun testModDecl()           = doTestResolved("mod_decl/main.rs", "mod_decl/foo.rs")

    // Check whether resolving-procedure (presumably) fails

    fun testModDeclNonOwn()     = doTestUnresolved( "mod_decl_non_own/foo.rs",
                                                    "mod_decl_non_own/main.rs",
                                                    "mod_decl_non_own/bar.rs")
}
