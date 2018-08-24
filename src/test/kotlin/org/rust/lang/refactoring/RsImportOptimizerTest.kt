/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsImportOptimizerTest: RsTestBase() {

    fun `test should do nothing`() = doTest("""
        use foo;
    """, """
        use foo;
    """)

    fun `test extern crates should be sorted`() = doTest("""
        extern crate b;
        extern crate a;
        extern crate c;
    """, """
        extern crate a;
        extern crate b;
        extern crate c;

    """)

    fun `test extern crates should be sorted after inner attributes`() = doTest("""
        #![allow(non_snake_case)]
        extern crate b;
        extern crate a;
        extern crate c;
    """, """
        #![allow(non_snake_case)]
        extern crate a;
        extern crate b;
        extern crate c;

    """)

    fun `test should be at the beginning`() = doTest("""
        //! test
        extern crate log;

        fn test() {}
        use foo;
    """, """
        //! test
        extern crate log;

        use foo;

        fn test() {}

    """)

    fun `test should be after inner attributes 1`() = doTest("""
        #![allow(non_snake_case)]
        extern crate log;

        use foo;
        use bar;
    """, """
        #![allow(non_snake_case)]
        extern crate log;

        use bar;
        use foo;

    """)

    fun `test should be after inner attributes 2`() = doTest("""
        #![allow(non_snake_case)]

        use foo;
        use bar;
    """, """
        #![allow(non_snake_case)]

        use bar;
        use foo;

    """)

    fun `test sort alphabetical of useItem`() = doTest("""
        use foo;
        use bar;
    """, """
        use bar;
        use foo;

    """)

    fun `test sort alphabetical of multi layer paths`() = doTest("""
        use test::foo;
        use test::bar;
    """, """
        use test::bar;
        use test::foo;

    """)

    fun `test sort alphabetical of useSpeck`() = doTest("""
        use foo::{test, foo, bar};
    """, """
        use foo::{bar, foo, test};
    """)

    fun `test sort alphabetical with self at start`() = doTest("""
        use foo::{test, foo, bar, self};
    """, """
        use foo::{self, bar, foo, test};
    """)

    fun `test sort alphabetical with self at star`() = doTest("""
        use foo::{test, foo, bar, *};
    """, """
        use foo::{*, bar, foo, test};
    """)

    fun `test sort alphabetical with multiple layer`() = doTest("""
        use foo::bar;
        use bar::bar;
    """, """
        use bar::bar;
        use foo::bar;

    """)

    fun `test sort alphabetical with multiple layer of groups`() = doTest("""
        use foo::{
            baz::{Test, quux::Bar},
            bar::{Foo, Bar},
        };
    """, """
        use foo::{
            bar::{Bar, Foo},
            baz::{quux::Bar, Test},
        };
    """)

    fun `test should sort mod areas by its own`() = doTest("""
        use foo;
        use bar;

        mod test {
            use test2;
            use deep;

            mod level2 {
                use foo;
                use bar;
            }
        }
    """, """
        use bar;
        use foo;

        mod test {
            use deep;
            use test2;

            mod level2 {
                use bar;
                use foo;
            }
        }
    """)

    fun `test remove curly braces simple`() = doTest(
        "use std::{mem};",
        "use std::mem;"
    )

    fun `test remove curly braces with no path`() = doTest(
        "use {std};",
        "use std;"
    )

    fun `test remove curly braces longer`() = doTest(
        "use foo::bar::baz::{qux};",
        "use foo::bar::baz::qux;"
    )

    fun `test remove curly braces extra`() = doTest("""
        #[macro_use]
        pub use /*comment*/ std::{mem};
    """, """
        #[macro_use]
        pub use /*comment*/ std::mem;
    """)

    fun `test do not move use items from test mod`() = doTest("""
        use std::io::Read;

        #[cfg(test)]
        mod test {
            use std::io::Write;
        }
    """, """
        use std::io::Read;

        #[cfg(test)]
        mod test {
            use std::io::Write;
        }
    """)

    fun `test sort inner modules if parent module does not have use items`() = doTest("""
        mod baz {
            use foo;
            use bar;
        }
    """, """
        mod baz {
            use bar;
            use foo;
        }
    """)

    private fun doTest(@Language("Rust") code: String, @Language("Rust") excepted: String){
        checkByText(code.trimIndent(), excepted.trimIndent()) {
            myFixture.performEditorAction("OptimizeImports")
        }
    }
}
