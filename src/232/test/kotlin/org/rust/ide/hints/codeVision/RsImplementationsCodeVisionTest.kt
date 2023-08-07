/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase
import org.intellij.lang.annotations.Language

class RsImplementationsCodeVisionTest : CodeVisionTestCase() {
    override val onlyCodeVisionHintsAllowed: Boolean = false

    fun `test no implementations`() = doTest("""
        trait Trait {
            fn foo();
            fn bar() {}

            const FOO: u32 = 0;
            type BAR;
        }
    """)

    fun `test trait single implementation`() = doTest("""
        <# block [1 usage   1 implementation] #>
        trait Trait {}
        impl Trait for () {}
    """)

    fun `test trait multiple implementations`() = doTest("""
        <# block [2 usages   2 implementations] #>
        trait Trait {}
        impl Trait for () {}
        impl Trait for u32 {}
    """)

    fun `test function single implementation`() = doTest("""
        <# block [1 usage   1 implementation] #>
        trait Trait {
        <# block [1 implementation] #>
            fn foo();
        }
        impl Trait for () {
            fn foo() {}
        }
    """)

    fun `test function multiple implementations`() = doTest("""
        <# block [2 usages   2 implementations] #>
        trait Trait {
        <# block [2 implementations] #>
            fn foo();
        }
        impl Trait for () {
            fn foo() {}
        }
        impl Trait for u32 {
            fn foo() {}
        }
    """)

    fun `test function single override`() = doTest("""
        <# block [1 usage   1 implementation] #>
        trait Trait {
        <# block [1 override] #>
            fn foo() {}
        }
        impl Trait for () {
            fn foo() {}
        }
    """)

    fun `test function multiple overrides`() = doTest("""
        <# block [2 usages   2 implementations] #>
        trait Trait {
        <# block [2 overrides] #>
            fn foo() {}
        }
        impl Trait for () {
            fn foo() {}
        }
        impl Trait for u32 {
            fn foo() {}
        }
    """)

    fun `test constant implementation`() = doTest("""
        <# block [1 usage   1 implementation] #>
        trait Trait {
        <# block [1 implementation] #>
            const FOO: u32;
        }
        impl Trait for () {
            const FOO: u32 = 5;
        }
    """)

    fun `test function override`() = doTest("""
        <# block [1 usage   1 implementation] #>
        trait Trait {
        <# block [1 override] #>
            const FOO: u32 = 0;
        }
        impl Trait for () {
            const FOO: u32 = 5;
        }
    """)

    fun `test type alias implementation`() = doTest("""
        <# block [1 usage   1 implementation] #>
        trait Trait {
        <# block [1 implementation] #>
            type TYPE;
        }
        impl Trait for () {
            type TYPE = ();
        }
    """)

    fun `test type alias override`() = doTest("""
        <# block [1 usage   1 implementation] #>
        trait Trait {
        <# block [1 override] #>
            type TYPE = ();
        }
        impl Trait for () {
            type TYPE = u32;
        }
    """)

    private fun doTest(@Language("Rust") text: String) {
        testProviders(text.trimIndent(), "main.rs", RsImplementationsCodeVisionProvider.ID)
    }
}
