/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.inspections.lints.RsUnusedImportInspection

@WithEnabledInspections(RsUnusedImportInspection::class)
class RsImportOptimizerTest: RsTestBase() {

    fun `test should do nothing`() = doTest("""
        use foo;

        fn main() {}
    """, """
        use foo;

        fn main() {}
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

        fn main() {}
    """, """
        #![allow(non_snake_case)]
        extern crate log;

        use bar;
        use foo;

        fn main() {}
    """)

    fun `test should be after inner attributes 2`() = doTest("""
        #![allow(non_snake_case)]

        use foo;
        use bar;

        fn main() {}
    """, """
        #![allow(non_snake_case)]

        use bar;
        use foo;

        fn main() {}
    """)

    fun `test sort alphabetical of useItem`() = doTest("""
        use foo;
        use bar;

        fn main() {}
    """, """
        use bar;
        use foo;

        fn main() {}
    """)

    fun `test sort alphabetical of multi layer paths`() = doTest("""
        use test::foo;
        use test::bar;

        fn main() {}
    """, """
        use test::bar;
        use test::foo;

        fn main() {}
    """)

    fun `test sort alphabetical of useSpeck`() = doTest("""
        use foo::{test, foo, bar};

        fn main() {}
    """, """
        use foo::{bar, foo, test};

        fn main() {}
    """)

    fun `test sort alphabetical with self at start`() = doTest("""
        use foo::{test, foo, bar, self};

        fn main() {}
    """, """
        use foo::{self, bar, foo, test};

        fn main() {}
    """)

    fun `test sort alphabetical with self at star`() = doTest("""
        use foo::{test, foo, bar, *};

        fn main() {}
    """, """
        use foo::{*, bar, foo, test};

        fn main() {}
    """)

    fun `test sort alphabetical with multiple layer`() = doTest("""
        use foo::bar;
        use bar::bar;

        fn main() {}
    """, """
        use bar::bar;
        use foo::bar;

        fn main() {}
    """)

    fun `test sort alphabetical with multiple layer of groups`() = doTest("""
        use foo::{
            baz::{Test, quux::Bar},
            bar::{Foo, Bar},
        };

        fn main() {}
    """, """
        use foo::{
            bar::{Bar, Foo},
            baz::{quux::Bar, Test},
        };

        fn main() {}
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

    fun `test remove curly braces simple`() = doTest("""
        use std::{mem};

        fn main() {}
    """, """
        use std::mem;

        fn main() {}
    """)

    fun `test remove curly braces with no path`() = doTest("""
        use {std};

        fn main() {}
    """, """
        use std;

        fn main() {}
    """)

    fun `test remove curly braces longer`() = doTest("""
        use foo::bar::baz::{qux};

        fn main() {}
    """, """
        use foo::bar::baz::qux;

        fn main() {}
    """)

    fun `test remove curly braces extra`() = doTest("""
        #[macro_use]
        pub use /*comment*/ std::{mem};

        fn main() {}
    """, """
        #[macro_use]
        pub use /*comment*/ std::mem;

        fn main() {}
    """)

    fun `test remove braces if single import with alias`() = doTest("""
        use getopts::{A as B};

        fn main() {}
    """, """
        use getopts::A as B;

        fn main() {}
    """)

    fun `test remove braces if single import with alias and comma`() = doTest("""
        use getopts::{A as B,};

        fn main() {}
    """, """
        use getopts::A as B;

        fn main() {}
    """)

    fun `test won't remove braces if single import with alias with left comment`() = checkNotChanged("""
        use getopts::{/*comment*/A as B};

        fn main() {}
    """)

    fun `test won't remove braces if single import with alias with right comment`() = checkNotChanged("""
        use getopts::{A as B/*comment*/};

        fn main() {}
    """)

    fun `test won't remove braces if single import with alias with inner left comment`() = checkNotChanged("""
        use getopts::{A /*comment*/as B};

        fn main() {}
    """)

    fun `test won't remove braces if single import with alias with inner right comment`() = checkNotChanged("""
        use getopts::{A as /*comment*/ B};

        fn main() {}
    """)

    fun `test remove braces if single import with extra comma`() = doTest("""
        use getopts::{optopt,};

        fn main() {}
    """, """
        use getopts::optopt;

        fn main() {}
    """)

    fun `test won't remove braces if import with left comment`() = checkNotChanged("""
        use getopts::{/*comment*/optopt};

        fn main() {}
    """)

    fun `test won't remove braces if import with right comment`() = checkNotChanged("""
        use getopts::{optopt/*comment*/};

        fn main() {}
    """)

    fun `test wont remove braces if multi import`() = checkNotChanged("""
        use getopts::{optarg, optopt};

        fn main() {}
    """)

    fun `test won't remove braces for single self`() = checkNotChanged("""
        use getopts::{self};

        fn main() {}
    """)

    fun `test remove braces with multiple imports`() = doTest("""
        use getopts::{optopt};
        use getopts::{A as B};
        use std::io::{self, Read, Write};
        use std::Vec::{Vec};
        use std::Vec::{Vec,};


        fn main() {}
    """, """
        use getopts::optopt;
        use getopts::A as B;
        use std::io::{self, Read, Write};
        use std::Vec::Vec;
        use std::Vec::Vec;

        fn main() {}
    """)

    fun `test remove empty use item 1`() = doTest("""
        use foo::{};

        fn main() {}
    """, """
        fn main() {}
    """)

    fun `test remove empty use item 2`() = doTest("""
        use aaa::foo;
        use bbb::{};
        use ccc::foo;

        fn main() {}
    """, """
        use aaa::foo;
        use ccc::foo;

        fn main() {}
    """)

    fun `test remove empty use group`() = doTest("""
        use aaa1::{zzz::{}};
        use aaa2::{zzz::{{}}};
        use aaa3::{zzz::{}, bbb};
        use aaa4::{bbb, zzz::{}};
        use aaa5::{bbb, zzz::{}, ccc};
        use aaa6::{bbb, yyy::{}, zzz::{}, ccc};
        use aaa7::{bbb, yyy::{}, ccc, zzz::{}, ddd};
        use aaa8::{bbb::{ccc::{}, ddd::{{}}}, zzz, eee::{}};
        use aaa9::{bbb::{ccc::{}, ddd::{{}}}, eee::{}};

        fn main() {}
    """, """
        use aaa3::bbb;
        use aaa4::bbb;
        use aaa5::{bbb, ccc};
        use aaa6::{bbb, ccc};
        use aaa7::{bbb, ccc, ddd};
        use aaa8::zzz;

        fn main() {}
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

    fun `test do not move use items before mod block`() = doTest("""
        pub mod foo {
            use std::time;
            use std::io;
        }
    """, """
        pub mod foo {
            use std::io;
            use std::time;
        }
    """)

    @ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
    fun `test group imports by semantics`() = doTest("""
        extern crate dep_lib_target;

        use bbb::{fff, eee};
        use aaa::bbb::ccc;
        use std::string;
        use std::{mem, io};
        use dep_lib_target;

        fn foo(_: string::String, _: mem::ManuallyDrop<u32>, _: io::ErrorKind) {}

        fn main() {}
    """, """
        extern crate dep_lib_target;

        use std::{io, mem};
        use std::string;

        use dep_lib_target;

        use aaa::bbb::ccc;
        use bbb::{eee, fff};

        fn foo(_: string::String, _: mem::ManuallyDrop<u32>, _: io::ErrorKind) {}

        fn main() {}
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test group imports by semantics with mod`() = doTest("""
        use bbb::{fff, eee};
        use super::ccc::bbb;
        use aaa::bbb;
        use crate::bbb::ccc;
        use crate::aaa::bbb;
        use self::aaa;
        use sss::aaa;
        use std::{mem, string, io};
        mod aaa {
            use bbb::*;
            use xxx::yyy;
            use super::aaa::bbb::ccc;
            use std::io;
            pub mod bbb {
                pub mod ccc {}
            }

            fn usage(p1: ccc::S, p2: io::S) {}
        }

        fn usage(p1: ccc::S, p2: mem::S, p3: string:S, p4: io::S) {}
    """, """
        use std::{io, mem, string};

        use aaa::bbb;
        use bbb::{eee, fff};
        use sss::aaa;

        use crate::aaa::bbb;
        use crate::bbb::ccc;

        use super::ccc::bbb;

        use self::aaa;

        mod aaa {
            use std::io;

            use bbb::*;
            use xxx::yyy;

            use super::aaa::bbb::ccc;

            pub mod bbb {
                pub mod ccc {}
            }

            fn usage(p1: ccc::S, p2: io::S) {}
        }

        fn usage(p1: ccc::S, p2: mem::S, p3: string:S, p4: io::S) {}
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test group imports by semantics remove extra newlines`() = doTest("""
        use aaa::bbb;
        mod aaa {
            pub mod bbb {
            }
        }

        use ccc;
        use std::{mem, string, io};


        mod ccc {}

        fn usage(p1: bbb::S, p2: mem::S, p3: string:S, p4: io::S) {}
    """, """
        use std::{io, mem, string};

        use aaa::bbb;
        use ccc;

        mod aaa {
            pub mod bbb {
            }
        }

        mod ccc {}

        fn usage(p1: bbb::S, p2: mem::S, p3: string:S, p4: io::S) {}
    """)

    fun `test remove unused use item`() = doTest("""
        struct S;

        mod foo {
            use crate::S;
        }
    """, """
        struct S;

        mod foo {}
    """)

    fun `test remove unused use speck at the beginning`() = doTest("""
        struct S;

        mod foo {
            use crate::{S, T};
        }
    """, """
        struct S;

        mod foo {
            use crate::T;
        }
    """)

    fun `test remove unused use speck in the middle`() = doTest("""
        struct S;

        mod foo {
            use crate::{R, S, T};
        }
    """, """
        struct S;

        mod foo {
            use crate::{R, T};
        }
    """)

    fun `test remove multiple unused use specks`() = doTest("""
        struct S1;
        struct S2;

        mod foo {
            use crate::{R, S1, S2, T};
        }
    """, """
        struct S1;
        struct S2;

        mod foo {
            use crate::{R, T};
        }
    """)

    fun `test remove unused use speck at the end`() = doTest("""
        struct S;

        mod foo {
            use crate::{T, S};
        }
    """, """
        struct S;

        mod foo {
            use crate::T;
        }
    """)

    fun `test remove empty group after unused specks are removed`() = doTest("""
        struct S1;
        struct S2;

        mod foo {
            use crate::{S1, S2};
        }
    """, """
        struct S1;
        struct S2;

        mod foo {}
    """)

    fun `test remove multiple unused use items`() = doTest("""
        struct S1;
        struct S2;

        mod foo {
            use crate::S1;
            use crate::S2;
        }
    """, """
        struct S1;
        struct S2;

        mod foo {}
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test do not remove cfg-disabled import`() = checkNotChanged("""
        mod foo {
            pub struct S {}
        }

        mod bar {
            #[cfg(not(intellij_rust))]
            use crate::foo::S;
        }
    """)

    private fun doTest(@Language("Rust") code: String, @Language("Rust") excepted: String) =
        checkEditorAction(code, excepted, "OptimizeImports")

    private fun checkNotChanged(@Language("Rust") code: String) = doTest(code, code)
}
