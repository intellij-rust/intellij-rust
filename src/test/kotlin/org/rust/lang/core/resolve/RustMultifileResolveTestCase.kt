package org.rust.lang.core.resolve

import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCase
import org.rust.lang.core.resolve.ref.RustReference


class RustMultiFileResolveTestCase : RustResolveTestCaseBase() {

    private fun doTest(vararg files: String) {
        myFixture.configureByFiles(*files)

        val usage = file.findReferenceAt(myFixture.caretOffset)!! as RustReference
        val declaration = usage.resolve()

        assertThat(declaration).isNotNull()
    }

    fun testChildMod()          = doTest("child_mod/main.rs", "child_mod/child.rs")
    fun testNestedChildMod()    = doTest("nested_child_mod/main.rs", "nested_child_mod/inner/child.rs")
    fun testGlobalPath()        = doTest("global_path/foo.rs", "global_path/bar.rs", "global_path/lib.rs")
    fun testUseViewPath()       = doTest("global_path/foo.rs", "global_path/bar.rs", "global_path/lib.rs")
    fun testModDecl()           = doTest("mod_decl/main.rs", "mod_decl/foo.rs")
    fun testModDeclNonOwn()     = doTest("mod_decl_non_own/foo.rs", "mod_decl_non_own/bar.rs")
}
