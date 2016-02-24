package org.rust.lang.core.resolve

class RustMultiFileResolveTestCaseExecutableTarget : RustMultiFileResolveTestCaseBase() {

    // Check whether resolving-procedure succeeds

    fun testChildMod()          = doTestResolved("child_mod/main.rs", "child_mod/child.rs")
    fun testNestedChildMod()    = doTestResolved("nested_child_mod/main.rs", "nested_child_mod/inner/child.rs")
    fun testModDecl()           = doTestResolved("mod_decl/main.rs", "mod_decl/foo.rs")
    fun testModDecl2()          = doTestResolved("mod_decl2/foo/mod.rs", "mod_decl2/main.rs", "mod_decl2/bar.rs")
    fun testUseFromChild()      = doTestResolved("use_from_child/main.rs", "use_from_child/child.rs")

    // Check whether resolving-procedure (presumably) fails

    fun testModDeclNonOwn()     = doTestUnresolved( "mod_decl_failure/foo.rs",
                                                    "mod_decl_failure/main.rs",
                                                    "mod_decl_failure/bar.rs")
}
