package org.rust.lang.core.resolve

class RustMultiFileResolveTest : RustMultiFileResolveTestBase() {
    // Check whether resolving-procedure succeeds

    fun testModDeclPath() = doTestResolved("mod_decl_path/main.rs", "mod_decl_path/bar/baz/foo.rs")
    fun testModDeclPathSuper() = doTestResolved("mod_decl_path_super/bar/baz/quux.rs", "mod_decl_path_super/main.rs")
    fun testModRelative() = doTestResolved("mod_relative/main.rs", "mod_relative/sub.rs", "mod_relative/foo.rs")
    fun testModRelative2() = doTestResolved("mod_relative2/main.rs", "mod_relative2/sub/mod.rs", "mod_relative2/foo.rs")
    fun testUseFromChild() = doTestResolved("use_from_child/main.rs", "use_from_child/child.rs")
    fun testGlobalPath() = doTestResolved("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
    fun testGlobalPathInline() = doTestResolved("global_path_inline/main.rs")

    // We resolve mod_decls even if the parent module does not own a directory and mod_decl should not be allowed.
    // This way, we don't need to know the set of crate roots for resolve, which helps indexing.
    // The `mod_decl not allowed here` error is then reported by an annotator.
    fun testModDeclNonOwn() = doTestResolved("mod_decl_non_own/foo.rs", "mod_decl_non_own/main.rs", "mod_decl_non_own/bar.rs")

    // Check whether resolving-procedure (presumably) fails

    fun testModDeclWrongPath() = doTestUnresolved("mod_decl_wrong_path/main.rs")

    fun testModDeclCycle() = doTestUnresolved("mod_decl_cycle/foo.rs", "mod_decl_cycle/bar.rs", "mod_decl_cycle/baz.rs")
}

