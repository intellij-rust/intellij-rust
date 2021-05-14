/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText

class RsLazyBlockStubCreationTest : RsLazyBlockStubCreationTestBase() {

    fun `test raw ref`() = doTest("""
    //- main.rs
        fn main() {
            let a = 123;
            let b = &raw const a;
        }
    """)

    fun `test lifetime parameter in function body`() = doTest("""
    //- main.rs
        struct S<'a, T: ?Sized>(&'a T);
        fn main() {
            let lambda: &dyn for<'b> Fn(&'b str) -> S<'b, str> = &|s| S(s);
        }
    """)

    fun `test macro_rules`() = doTest("""
    //- main.rs
        fn main() {
            macro_rules! foo {}
        }
    """)

    fun `test nested macro_rules`() = doTest("""
    //- main.rs
        fn main() {
            {
                macro_rules! foo {}
            };
        }
    """)

    private fun doTest(@Language("Rust") fileTreeText: String) {
        fileTreeFromText(fileTreeText).create()
        checkRustFiles(myFixture.findFileInTempDir("."), emptyList())
    }
}
