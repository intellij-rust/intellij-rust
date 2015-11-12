package org.rust.lang.core.resolve

import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCase
import org.rust.lang.core.resolve.ref.RustReference


class RustProjResolveTestCase : RustTestCase() {
    override fun getTestDataPath() = "testData/org/rust/lang/core/resolve/fixtures"

    private fun doTest(crateRoot: String, mod: String) {
        myFixture.configureByFiles(crateRoot, mod)

        val usage = file.findReferenceAt(myFixture.caretOffset)!! as RustReference
        val declaration = usage.resolve()

        assertThat(declaration).isNotNull()
    }

    fun testChildMod() = doTest("child_mod/main.rs", "child_mod/child.rs")
    fun testNestedChildMod() = doTest("nested_child_mod/main.rs", "nested_child_mod/inner/child.rs")
}
