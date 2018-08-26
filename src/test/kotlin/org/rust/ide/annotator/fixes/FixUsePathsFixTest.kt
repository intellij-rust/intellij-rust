/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.annotator.RsAnnotationTestBase
import org.rust.MockEdition

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class FixUsePathsFixTest : RsAnnotationTestBase() {

    fun `test add crate keyword 1`() = checkFixByText("Add `crate` at the beginning of path", """
        mod foo {
            pub struct Foo;
        }

        use <error descr="Paths in `use` declarations should start with a crate name, or with `crate`, `super`, or `self`">foo::Foo/*caret*/</error>;
    """, """
        mod foo {
            pub struct Foo;
        }

        use crate::foo::Foo/*caret*/;
    """)

    fun `test add crate keyword 2`() = checkFixByText("Add `crate` at the beginning of path", """
        mod foo {
            pub struct Foo;
        }

        use <error descr="Paths in `use` declarations should start with a crate name, or with `crate`, `super`, or `self`">::foo::Foo/*caret*/</error>;
    """, """
        mod foo {
            pub struct Foo;
        }

        use crate::foo::Foo/*caret*/;
    """)

    fun `test add crate keyword with use group`() = checkFixByText("Add `crate` at the beginning of path", """
        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        use <error descr="Paths in `use` declarations should start with a crate name, or with `crate`, `super`, or `self`">foo/*caret*/</error>::{Foo, Bar};
    """, """
        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        use crate::foo/*caret*/::{Foo, Bar};
    """)

    fun `test add crate keyword in use group`() = checkFixByText("Add `crate` at the beginning of path", """
        mod foo {
            pub struct Foo;
        }

        use {<error descr="Paths in `use` declarations should start with a crate name, or with `crate`, `super`, or `self`">foo::Foo</error>/*caret*/};
    """, """
        mod foo {
            pub struct Foo;
        }

        use {crate::foo::Foo/*caret*/};
    """)

    fun `test do not insert crate keyword for unknown items`() = checkFixIsUnavailable("Add `crate` at the beginning of path", """
        use <error descr="Paths in `use` declarations should start with a crate name, or with `crate`, `super`, or `self`">foo::Foo/*caret*/</error>;
    """)

    fun `test do not insert crate keyword for`() = checkFixIsUnavailable("Add `crate` at the beginning of path", """
        mod bar {
            pub mod foo {
                pub struct Foo;
            }
        }

        use crate::bar::foo;
        use <error descr="Paths in `use` declarations should start with a crate name, or with `crate`, `super`, or `self`">foo::Foo/*caret*/</error>;
    """)
}
