/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsInvalidReexportErrorAnnotatorTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test reexport of public item from accessible module`() = checkErrors("""
        mod foo {
            mod bar {
                pub struct Foo;
                pub mod foo {}
            }
            pub use bar::Foo;
            pub use bar::foo;
        }
    """)

    fun `test E0603 reexport of public item from inaccessible module`() = checkErrors("""
        mod foo {
            mod bar {
                mod baz {}
            }
            pub use <error descr="Module `bar::baz` is private [E0603]">bar::baz</error>;
        }
    """)

    fun `test E0365 reexport of private module`() = checkErrors("""
        mod foo {
            mod bar {}
            pub use <error descr="`bar` is private, and cannot be re-exported [E0365]">bar</error> as baz;
        }
    """)

    fun `test E0365 star reexport of private module`() = checkErrors("""
        mod foo {
            mod bar {}
            pub use bar::*;
        }
    """)

    fun `test E0364 reexport of private item`() = checkErrors("""
        mod foo {
            struct Foo;
            pub use <error descr="`Foo` is private, and cannot be re-exported [E0364]">Foo</error> as Bar;
        }
    """)

    fun `test E0364 reexport of private item from parent module`() = checkErrors("""
        mod foo {
            fn foo() {}

            mod bar {
                pub use super::<error descr="`foo` is private, and cannot be re-exported [E0364]">foo</error>;
            }
        }
    """)

    fun `test reexport item with duplicated name`() = checkErrors("""
        mod foo {
            pub struct <error descr="A type named `A` has already been defined in this module [E0428]">A</error> { a: u32 }
            struct <error descr="A type named `A` has already been defined in this module [E0428]">A</error>;
            pub use A as B;
        }
    """)

    fun `test reexport multiple namespaces`() = checkErrors("""
        mod foo {
            pub struct A { a: u32 }
            const A: u32 = 0;

            pub use A as B;
        }
        mod bar {
            struct A { a: u32 }
            pub const A: u32 = 0;

            pub use A as B;
        }
    """)

    fun `test reexport module path 1`() = checkErrors("""
        mod foo {
            mod bar {
                pub(in crate) struct Foo;
            }
            mod baz {
                pub(in super) use super::bar::Foo;
            }
        }
    """)

    fun `test reexport module path 2`() = checkErrors("""
        mod foo {
            mod bar {
                pub(in super) struct Foo;
            }
            mod baz {
                pub(in super) use super::bar::Foo;
            }
        }
    """)

    fun `test reexport module path 3`() = checkErrors("""
        mod foo {
            mod bar {
                pub struct Foo;
            }
            mod baz {
                pub(in super) use super::bar::Foo;
            }
        }
    """)

    fun `test E0364 reexport module path 4`() = checkErrors("""
        mod foo {
            mod bar {
                pub(in super) struct Foo;
            }
            mod baz {
                pub(in crate) use super::bar::<error descr="`Foo` is private, and cannot be re-exported [E0364]">Foo</error>;
            }
        }
    """)

    fun `test E0364 reexport module path 5`() = checkErrors("""
        mod foo {
            mod bar {
                pub(in crate) struct Foo;
            }
            mod baz {
                pub use super::bar::<error descr="`Foo` is private, and cannot be re-exported [E0364]">Foo</error>;
            }
        }
    """)

    fun `test E0365 self reexport`() = checkErrors("""
        mod foo {
            mod bar {}
            pub use bar::{<error descr="`bar` is private, and cannot be re-exported [E0365]">self</error> as baz};
        }
    """)

    fun `test E0365 group reexport`() = checkErrors("""
        mod foo {
            mod bar {
                pub mod foo {}
                pub(in super) mod baz {}
            }
            pub use bar::{foo, <error descr="`baz` is private, and cannot be re-exported [E0365]">baz</error>};
        }
    """)

    fun `test E0365 reexport parent`() = checkErrors("""
        mod foo {
            pub use super::{<error descr="`foo` is private, and cannot be re-exported [E0365]">foo</error>};
        }
    """)

    fun `test reexport of private item into the same module`() = checkErrors("""
        mod foo {
            mod bar {}
            pub(in crate::foo) use bar as baz;
        }
    """)

    fun `test reexport of private item into child module`() = checkErrors("""
        mod foo {
            mod bar {
                pub(in crate::foo::bar) use crate::foo::bar as qwe;
            }
        }
    """)

    fun `test reexport of private item into sibling child module`() = checkErrors("""
        mod foo {
            mod bar {}
            mod baz {
                pub(in crate::foo::baz) use crate::foo::bar as qwe;
            }
        }
    """)

    fun `test rename of private item`() = checkErrors("""
        mod foo {
            mod bar {}
            use bar as baz;
        }
    """)

    fun `test make public fix`() = checkFixByText("Make `bar` public", """
        pub mod foo {
            mod bar {}
            pub use <error>bar/*caret*/</error> as baz;
        }
    """, """
        pub mod foo {
            pub mod bar {}
            pub use bar/*caret*/ as baz;
        }
    """)

    fun `test make public fix for item with restricted visibility`() = checkFixIsUnavailable("Make `bar` public", """
        pub mod foo {
            pub(crate) mod bar {}
            pub use <error>bar/*caret*/</error> as baz;
        }
    """)
}
