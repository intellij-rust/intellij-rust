package org.rust.lang.core.resolve

class RustMultiFileResolveTestCase : RustMultiFileResolveTestCaseBase() {
    // Check whether resolving-procedure succeeds

    fun testChildMod()          = doTestResolved("child_mod/main.rs", "child_mod/child.rs")
    fun testNestedChildMod()    = doTestResolved("nested_child_mod/main.rs", "nested_child_mod/inner/child.rs")
    fun testModDecl()           = doTestResolved("mod_decl/main.rs", "mod_decl/foo.rs")
    fun testModDecl2()          = doTestResolved("mod_decl2/foo/mod.rs", "mod_decl2/main.rs", "mod_decl2/bar.rs")
    fun testModDeclPath()       = doTestResolved("mod_decl_path/main.rs", "mod_decl_path/bar/baz/foo.rs")
    fun testUseFromChild()      = doTestResolved("use_from_child/main.rs", "use_from_child/child.rs")
    fun testGlobalPath()        = doTestResolved("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
    fun testUseViewPath()       = doTestResolved("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")

    // Check whether resolving-procedure (presumably) fails

    fun testModDeclNonOwn()     = doTestUnresolved("mod_decl_failure/foo.rs",
                                                   "mod_decl_failure/main.rs",
                                                   "mod_decl_failure/bar.rs")

    fun testModDeclWrongPath() = doTestUnresolved("mod_decl_wrong_path/main.rs")
}
