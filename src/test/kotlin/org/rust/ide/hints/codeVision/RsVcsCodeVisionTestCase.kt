/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.codeVision

import com.intellij.codeInsight.hints.VcsCodeVisionProvider
import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase
import org.intellij.lang.annotations.Language


class RsVcsCodeVisionTestCase : CodeVisionTestCase() {
    fun `test function`() = doTest("""
        /*<# block [John Smith +2] #>*/
        fn foo() {}
    """)

    fun `test struct`() = doTest("""
        /*<# block [John Smith +2] #>*/
        struct S {
            field: i32,
        }
    """)

    fun `test enum`() = doTest("""
        /*<# block [John Smith +2] #>*/
        enum E {
            V1,
            V2
        }
    """)

    fun `test impl`() = doTest("""
        /*<# block [John Smith +2] #>*/
        struct S;

        /*<# block [John Smith +2] #>*/
        impl S {
        /*<# block [John Smith +2] #>*/
            fn foo() {}
        /*<# block [John Smith +2] #>*/
            fn bar(&self) {}
        /*<# block [John Smith +2] #>*/
            const C: i32 = 1;
        }
    """)

    fun `test trait`() = doTest("""
        /*<# block [John Smith +2] #>*/
        trait Trait {
        /*<# block [John Smith +2] #>*/
            fn foo();
        /*<# block [John Smith +2] #>*/
            fn bar(&self);
        /*<# block [John Smith +2] #>*/
            const C: i32;
        /*<# block [John Smith +2] #>*/
            type T;
        }
    """)

    fun `test inline mod`() = doTest("""
        /*<# block [John Smith +2] #>*/
        mod foo {}
    """)

    fun `test mod declaration`() = doTest("""
        /*<# block [John Smith +2] #>*/
        mod foo;
    """)

    fun `test macro`() = doTest("""
        /*<# block [John Smith +2] #>*/
        macro_rules! foo {}
    """)

    fun `test macro 2`() = doTest("""
        /*<# block [John Smith +2] #>*/
        macro foo() {}
    """)

    fun `test static`() = doTest("""
        /*<# block [John Smith +2] #>*/
        static S: i32 = 1;
        /*<# block [John Smith +2] #>*/
        static mut M: i32 = 1;
    """)

    fun `test const`() = doTest("""
        /*<# block [John Smith +2] #>*/
        const S: i32 = 1;
    """)

    private fun doTest(@Language("Rust") text: String) {
        testProviders(text.trimIndent(), "main.rs", VcsCodeVisionProvider.id)
    }
}
