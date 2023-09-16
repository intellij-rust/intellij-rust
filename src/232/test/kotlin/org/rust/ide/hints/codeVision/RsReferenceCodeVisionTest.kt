/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase
import org.intellij.lang.annotations.Language

class RsReferenceCodeVisionTest : CodeVisionTestCase() {
    override val onlyCodeVisionHintsAllowed: Boolean = false

    fun `test no usages`() = doTest("""
        fn foo() {}
    """)

    fun `test function`() = doTest("""
        /*<# block [1 usage] #>*/
        fn foo() {}

        fn bar() {
            foo();
        }
    """)

    fun `test function multiple usages`() = doTest("""
        /*<# block [2 usages] #>*/
        fn foo() {}

        fn bar() {
            foo();
        }
        fn baz() {
            foo();
        }
    """)

    fun `test struct`() = doTest("""
        /*<# block [2 usages] #>*/
        struct S;

        fn bar(s: S) {
            let a: S = s;
        }
    """)

    fun `test struct field`() = doTest("""
        /*<# block [1 usage] #>*/
        struct S {
        /*<# block [2 usages] #>*/
            a: u32
        }

        fn bar(s: S) {
            let a = s.a;
            s.a = a;
        }
    """)

    fun `test enum`() = doTest("""
        /*<# block [1 usage] #>*/
        enum E1 {
            V1,
            V2
        }

        fn bar(e: E1) {}
    """)

    fun `test enum variant`() = doTest("""
        /*<# block [1 usage] #>*/
        enum E1 {
        /*<# block [1 usage] #>*/
            V1,
            V2
        }

        fn bar() {
            let x = E1::V1;
        }
    """)

    fun `test trait`() = doTest("""
        /*<# block [1 usage] #>*/
        trait T {}

        fn bar(a: &dyn T) {}
    """)

    fun `test type alias`() = doTest("""
        /*<# block [1 usage] #>*/
        type T = u32;

        fn bar(a: T) {}
    """)

    fun `test constant`() = doTest("""
        /*<# block [1 usage] #>*/
        const FOO: u32 = 0;

        fn bar() {
            let a = FOO;
        }
    """)

    fun `test static`() = doTest("""
        /*<# block [1 usage] #>*/
        static FOO: u32 = 0;

        fn bar() {
            let a = FOO;
        }
    """)

    /* TODO: fix
    fun `test macro`() = doTest("""
        /*<# block [1 usage] #>*/
        macro_rules! foo{ {} => {} }

        fn bar() {
            let a = foo!();
        }
    """)*/

    fun `test macro 2`() = doTest("""
        /*<# block [1 usage] #>*/
        macro foo() {}

        fn bar() {
            let a = foo!();
        }
    """)

    fun `test mod`() = doTest("""
        /*<# block [1 usage] #>*/
        mod foo {
        /*<# block [1 usage] #>*/
            pub struct S;
        }

        fn bar(a: foo::S) {}
    """)

    private fun doTest(@Language("Rust") text: String) {
        Registry.get("org.rust.code.vision.usage.slow").setValue(true, testRootDisposable)
        testProviders(text.trimIndent(), "main.rs", RsReferenceCodeVisionProvider().groupId)
    }
}
