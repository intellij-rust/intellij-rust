/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.ui.TestDialog
import com.intellij.util.IncorrectOperationException
import org.rust.withTestDialog

class RsMoveFileTest : RsMoveFileTestBase() {
    override val dataPath = "org/rust/ide/refactoring/move/fixtures/"

    fun `test rename inside references`() = doTest("mod1/foo.rs", "mod2")

    fun `test rename outside references`() = doTest("mod1/mod1_inner/foo.rs", "mod2")

    fun `test simple`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo;
    //- mod2.rs
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2.rs
        mod foo;
    //- mod2/foo.rs
        pub fn func() {}
    """)

    fun `test parent modules are declared inline`() = doTest(
        "mod1/mod1_inner/foo.rs",
        "mod2/mod2_inner",
        """
    //- main.rs
        mod mod1 {
            mod mod1_inner {
                mod foo;
            }
        }
        mod mod2 {
            mod mod2_inner {}
        }
    //- mod1/mod1_inner/.gitkeep
    //- mod1/mod1_inner/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1 {
            mod mod1_inner {}
        }
        mod mod2 {
            mod mod2_inner { mod foo; }
        }
    //- mod1/mod1_inner/.gitkeep
    //- mod2/mod2_inner/foo.rs
        pub fn func() {}
    """)

    fun `test move mod declaration`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        /// example comment
        #[example_attr]
        pub mod foo;
    //- mod2.rs
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2.rs
        /// example comment
        #[example_attr]
        pub mod foo;
    //- mod2/foo.rs
        pub fn func() {}
    """)

    fun `test target directory does not exist`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo;
    //- mod2.rs
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2.rs
        mod foo;
    //- mod2/foo.rs
        pub fn func() {}
    """)

    fun `test move to root directory`() = doTest(
        "mod1/foo.rs",
        ".",
        """
    //- main.rs
        mod mod1;
    //- mod1/mod.rs
        mod foo;
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod foo;
    //- mod1/mod.rs
    //- foo.rs
        pub fn func() {}
    """)

    fun `test usages in other crates`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        extern crate test_package;
        fn test1() {
            use test_package::mod1::foo::func;
            func();
        }
        fn test2() {
            use test_package::mod1;
            mod1::foo::func();
            mod1::foo::func();
            mod1::foo::func();
        }
    //- lib.rs
        pub mod mod1;
        pub mod mod2;
    //- mod1/mod.rs
        pub mod foo;
    //- mod2/mod.rs
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        extern crate test_package;
        fn test1() {
            use test_package::mod2::foo::func;
            func();
        }
        fn test2() {
            use test_package::{mod1, mod2};
            mod2::foo::func();
            mod2::foo::func();
            mod2::foo::func();
        }
    //- lib.rs
        pub mod mod1;
        pub mod mod2;
    //- mod1/mod.rs
    //- mod2/mod.rs
        pub mod foo;
    //- mod2/foo.rs
        pub fn func() {}
    """)

    fun `test use relative path for inside references from parent mods`() = doTest(
        "mod1/foo.rs",
        "inner/mod2",
        """
    //- main.rs
        mod mod1;
        mod inner;
    //- mod1/mod.rs
        pub mod foo;
    //- inner/mod.rs
        use crate::mod1::foo::func;
        mod mod2;
    //- inner/mod2/mod.rs
        use crate::mod1::foo::func;
    //- mod1/foo.rs
        use self::foo_inner::foo_inner_func;
        mod foo_inner {
            use super::func;
            pub fn foo_inner_func() {}
        }
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod inner;
    //- mod1/mod.rs
    //- inner/mod.rs
        use mod2::foo::func;
        mod mod2;
    //- inner/mod2/mod.rs
        use foo::func;

        pub mod foo;
    //- inner/mod2/foo.rs
        use self::foo_inner::foo_inner_func;
        mod foo_inner {
            use super::func;
            pub fn foo_inner_func() {}
        }
        pub fn func() {}
    """)

    fun `test add 'self' prefix for paths if exists crate with same name`() = doTest(
        "mod1/foo.rs",
        "inner/test_package/mod2",
        """
    //- main.rs
        extern crate test_package;
        mod mod1;
        mod inner {
            mod test_package {
                pub mod mod2;
            }
            use crate::mod1::foo;
        }
    //- lib.rs
    //- mod1/mod.rs
        pub mod foo;
    //- inner/test_package/mod2/mod.rs
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        extern crate test_package;
        mod mod1;
        mod inner {
            mod test_package {
                pub mod mod2;
            }
            use self::test_package::mod2::foo;
        }
    //- lib.rs
    //- mod1/mod.rs
    //- inner/test_package/mod2/mod.rs
        pub mod foo;
    //- inner/test_package/mod2/foo.rs
        pub fn func() {}
    """)

    // test that we don't use `RsPath::text`, by adding comment containing :: inside `RsPath`
    // we keep existing reference style, so if we erroneously used `RsPath::text`,
    // then this comment will trigger replacing reference to `crate::mod2::foo` (and not to `mod2::foo`)
    fun `test ignore comments inside RsPath`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
        mod usage {
            fn test() {
                use crate::mod1;
                mod1::/*::*/foo::func();
            }
        }
    //- mod1/mod.rs
        pub mod foo;
    //- mod2.rs
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
        mod usage {
            fn test() {
                use crate::{mod1, mod2};
                mod2::foo::func();
            }
        }
    //- mod1/mod.rs
    //- mod2.rs
        pub mod foo;
    //- mod2/foo.rs
        pub fn func() {}
    """)

    fun `test don't search for references`() = doTest(
        arrayOf("mod1/foo.rs"),
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;

        fn main() {
            mod1::foo::func();
        }
    //- mod1/mod.rs
        mod foo;
    //- mod2/mod.rs
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;

        fn main() {
            mod1::foo::func();
        }
    //- mod1/mod.rs
        mod foo;
    //- mod2/mod.rs
    //- mod2/foo.rs
        pub fn func() {}
    """, searchForReferences = false)

    fun `test fail if mod already exists`() = expect<IncorrectOperationException> {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1;
            mod mod2;
        //- mod1/mod.rs
            mod foo;
        //- mod2/mod.rs
            mod foo { pub fn func_original() {} }
        //- mod1/foo.rs
            pub fn func() {}
        """)
    }

    // in this case we run default processor
    fun `test target directory not owned by mod`() = withTestDialog(TestDialog.OK) {
        doTest(
            "mod1/foo.rs",
            "mod2",
            """
        //- main.rs
            mod mod1;
        //- mod1/mod.rs
            mod foo;
        //- mod1/foo.rs
            pub fn func() {}
        """, """
        //- main.rs
            mod mod1;
        //- mod1/mod.rs
            mod foo;
        //- mod2/foo.rs
            pub fn func() {}
        """)
    }

    fun `test move multiple files`() = doTest(
        arrayOf("mod1/foo1.rs", "mod1/foo2.rs"),
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo1;
        mod foo2;
    //- mod2.rs
    //- mod1/foo1.rs
        pub fn func1() {}
    //- mod1/foo2.rs
        pub fn func2() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2.rs
        mod foo1;
        mod foo2;
    //- mod2/foo1.rs
        pub fn func1() {}
    //- mod2/foo2.rs
        pub fn func2() {}
    """)

    fun `test move directory containing one file`() = doTest(
        "foo",
        "mod2",
        """
    //- main.rs
        mod foo;
        mod mod2;
    //- mod2/mod.rs
    //- foo/mod.rs
        fn func() {}
    """, """
    //- main.rs
        mod mod2;
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo/mod.rs
        fn func() {}
    """)

    fun `test move directory containing two files`() = doTest(
        "foo",
        "mod2",
        """
    //- main.rs
        mod foo;
        mod mod2;
    //- mod2/mod.rs
    //- foo/mod.rs
        mod inner;
    //- foo/inner.rs
        fn inner_func() {}
    """, """
    //- main.rs
        mod mod2;
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo/mod.rs
        mod inner;
    //- mod2/foo/inner.rs
        fn inner_func() {}
    """)

    fun `test move directory when owning file is outside`() = doTest(
        "foo",
        "mod2",
        """
    //- main.rs
        mod foo;
        mod mod2;
    //- mod2/mod.rs
    //- foo.rs
        mod inner;
    //- foo/inner.rs
        fn inner_func() {}
    """, """
    //- main.rs
        mod mod2;
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo.rs
        mod inner;
    //- mod2/foo/inner.rs
        fn inner_func() {}
    """)

    fun `test move file when it is outside owning directory`() = doTest(
        "foo",
        "mod2",
        """
    //- main.rs
        mod foo;
        mod mod2;
    //- mod2/mod.rs
    //- foo.rs
        mod inner;
    //- foo/inner.rs
        fn inner_func() {}
    """, """
    //- main.rs
        mod mod2;
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo.rs
        mod inner;
    //- mod2/foo/inner.rs
        fn inner_func() {}
    """)

    fun `test move both file and directory when file is outside owning directory`() = doTest(
        arrayOf("foo", "foo.rs"),
        "mod2",
        """
    //- main.rs
        mod foo;
        mod mod2;
    //- mod2/mod.rs
    //- foo.rs
        mod inner;
    //- foo/inner.rs
        fn inner_func() {}
    """, """
    //- main.rs
        mod mod2;
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo.rs
        mod inner;
    //- mod2/foo/inner.rs
        fn inner_func() {}
    """)

    fun `test self reference from child mod of moved file`() = doTest(
        "foo",
        "mod2",
        """
    //- main.rs
        mod foo;
        mod mod2;
    //- mod2/mod.rs
    //- foo.rs
        mod inner;
        fn foo_func() {}
    //- foo/inner.rs
        fn inner_func() {
            use crate::foo;
            foo::foo_func();
        }
    """, """
    //- main.rs
        mod mod2;
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo.rs
        mod inner;
        fn foo_func() {}
    //- mod2/foo/inner.rs
        fn inner_func() {
            use crate::mod2::foo;
            foo::foo_func();
        }
    """)

    fun `test add mod declaration after doc comments`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo;
    //- mod2.rs
        //! comment
    //- mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2.rs
        //! comment
        mod foo;
    //- mod2/foo.rs
        pub fn func() {}
    """)
}
