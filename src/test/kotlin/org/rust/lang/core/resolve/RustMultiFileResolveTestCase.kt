package org.rust.lang.core.resolve

class RustMultiFileResolveTestCase : RustMultiFileResolveTestCaseBase() {
    // Check whether resolving-procedure succeeds

    fun testChildMod()          = doTestResolved("child_mod/main.rs", "child_mod/child.rs")
    fun testNestedChildMod()    = doTestResolved("nested_child_mod/main.rs", "nested_child_mod/inner/child.rs")
    fun testModDecl()           = doTestResolved("mod_decl/main.rs", "mod_decl/foo.rs")
    fun testModDecl2()          = doTestResolved("mod_decl2/foo/mod.rs", "mod_decl2/main.rs", "mod_decl2/bar.rs")
    fun testModDeclPath()       = doTestResolved("mod_decl_path/main.rs", "mod_decl_path/bar/baz/foo.rs")
    fun testModDeclPathSuper()  = doTestResolved("mod_decl_path_super/bar/baz/quux.rs", "mod_decl_path_super/main.rs")
    fun testModRelative()       = doTestResolved("mod_relative/main.rs", "mod_relative/sub.rs", "mod_relative/foo.rs")
    fun testModRelative2()      = doTestResolved("mod_relative2/main.rs", "mod_relative2/sub/mod.rs", "mod_relative2/foo.rs")
    fun testUseFromChild()      = doTestResolved("use_from_child/main.rs", "use_from_child/child.rs")
    fun testGlobalPath()        = doTestResolved("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
    fun testGlobalPathInline()  = doTestResolved("global_path_inline/main.rs")

    // We resolve mod_decls even if the parent module does not own a directory and mod_decl should not be allowed.
    // This way, we don't need to know the set of crate roots for resolve, which helps indexing.
    // The `mod_decl not allowed here` error is then reported by an annotator.
    fun testModDeclNonOwn()     = doTestResolved("mod_decl_non_own/foo.rs", "mod_decl_non_own/main.rs", "mod_decl_non_own/bar.rs")

    // Check whether resolving-procedure (presumably) fails

    fun testModDeclWrongPath() = doTestUnresolved("mod_decl_wrong_path/main.rs")

    fun testModDeclCycle()     = doTestUnresolved("mod_decl_cycle/foo.rs", "mod_decl_cycle/bar.rs", "mod_decl_cycle/baz.rs")
}
