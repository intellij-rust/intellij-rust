/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.util.IncorrectOperationException
import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsMoveFileTest : RsMoveFileTestBase() {
    override val dataPath = "org/rust/ide/refactoring/move/fixtures/"

    fun `test add import if there are many usages`() = doTest("mod1/foo.rs", "mod2")

    fun `test rename inside references`() = doTest("mod1/foo.rs", "mod2")

    fun `test rename outside references`() = doTest("mod1/mod1_inner/foo.rs", "mod2")

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
        use test_package::mod1::foo::func;
        fn test1() {
            use test_package::mod1::*;
            foo::func();
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
        use test_package::mod2::foo::func;
        fn test1() {
            use test_package::mod1::*;
            test_package::mod2::foo::func();
        }
        fn test2() {
            use test_package::mod1;
            use test_package::mod2::foo;
            foo::func();
            foo::func();
            foo::func();
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
    fun `test target directory not owned by mod`() {
        Messages.setTestDialog(TestDialog.OK)
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
}
