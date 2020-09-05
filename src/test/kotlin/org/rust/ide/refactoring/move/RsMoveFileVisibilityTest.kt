/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.refactoring.BaseRefactoringProcessor
import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsMoveFileVisibilityTest : RsMoveFileTestBase() {

    private fun expectConflicts(action: () -> Unit) {
        expect<BaseRefactoringProcessor.ConflictsInTestsException>(action)
    }

    fun `test outside reference to private item in old parent module`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1;
            mod mod2;
        //- mod1/mod.rs
            mod foo;
            mod bar;  // private
        //- mod2/mod.rs
        //- mod1/foo.rs
            pub fn func() {
                super::bar::bar_func();
            }
        //- mod1/bar.rs
            pub fn bar_func() {}
        """)
    }

    fun `test outside reference to item which has pub(in old_parent_mod) visibility`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1;
            mod mod2;
        //- mod1/mod.rs
            pub mod foo;
            pub mod bar;
        //- mod2/mod.rs
        //- mod1/foo.rs
            pub fn func() {
                super::bar::bar_func();
            }
        //- mod1/bar.rs
            pub(super) fn bar_func() {}  // private for mod2
        """)
    }

    fun `test outside reference to item in parent module, when parent module is private`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/mod1_inner/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1 {
                mod mod1_inner {
                    pub fn mod1_inner_func() {}
                    mod foo;
                }
            }
            mod mod2 {}
        //- mod1/mod1_inner/foo.rs
            use super::*;

            pub fn func() {
                mod1_inner_func();
            }
        """)
    }

    fun `test outside reference to item in parent module, when grandparent module is private`() = expectConflicts {
        doTestExpectError(
            arrayOf("inner1/inner2/mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod inner1 {
                mod inner2 {  // private
                    pub mod mod1 {
                        pub mod foo;
                        pub fn mod1_func() {}
                    }
                }
            }
            mod mod2 {}
        //- inner1/inner2/mod1/foo.rs
            pub fn func() {
                super::mod1_func();
            }
        """)
    }

    fun `test outside reference to item in grandparent module, when parent module is private`() = doTest(
        "mod1/mod1_inner/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod mod1_inner;  // private
        pub fn mod1_func() {}
    //- mod1/mod1_inner/mod.rs
        mod foo;
    //- mod2/mod.rs
    //- mod1/mod1_inner/foo.rs
        fn func() {
            super::super::mod1_func();
        }
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod mod1_inner;  // private
        pub fn mod1_func() {}
    //- mod1/mod1_inner/mod.rs
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo.rs
        use crate::mod1;

        fn func() {
            mod1::mod1_func();
        }
    """)

    fun `test access private field of struct in old parent module`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1 {
                pub struct A { field: i32 }
                pub mod foo;
            }
            mod mod2 {}
        //- mod1/foo.rs
            pub fn func(a: crate::mod1::A) {
                a.field;
            }
        """)
    }

    fun `test construct struct with private field from old parent module`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1 {
                pub mod foo;
                pub struct A { field: i32 }
            }
            mod mod2 {}
        //- mod1/foo.rs
            pub fn func() {
                let _ = crate::mod1::A { field: 0 };
            }
        """)
    }

    fun `test construct struct with private field from old parent module using shorthand`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1 {
                pub mod foo;
                pub struct A { field: i32 }
            }
            mod mod2 {}
        //- mod1/foo.rs
            pub fn func() {
                let field = 1;
                let _ = crate::mod1::A { field };
            }
        """)
    }

    fun `test construct struct with public field from old parent module using shorthand`() = doTest(
        arrayOf("mod1/foo.rs"),
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2 {}
    //- mod1/mod.rs
        pub struct A { pub field: i32 }
        mod foo;
    //- mod1/foo.rs
        pub fn func() {
            let field = 1;
            let _ = crate::mod1::A { field };
        }
    """, """
    //- main.rs
        mod mod1;
        mod mod2 { mod foo; }
    //- mod1/mod.rs
        pub struct A { pub field: i32 }
    //- mod2/foo.rs
        pub fn func() {
            let field = 1;
            let _ = crate::mod1::A { field };
        }
    """)

    fun `test construct struct with private field from old parent module using type alias`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1 {
                pub mod foo;
                pub struct Bar { field: i32 }
                pub type BarAlias = Bar;
            }
            mod mod2 {}
        //- mod1/foo.rs
            pub fn func() {
                let _ = crate::mod1::BarAlias { field: 0 };
            }
        """)
    }

    fun `test destructuring struct with private field 1`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1 {
                pub mod foo;
                pub struct Foo { x: i32 }
            }
            mod mod2 {}
        //- mod1/foo.rs
            pub fn func(foo: crate::mod1::Foo) {
                let crate::mod1::Foo { x } = foo;
            }
        """)
    }

    fun `test destructuring struct with private field 2`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        pub mod foo;
        pub struct Foo {
            pub field1: i32,
            field2: i32,
        }
    //- mod2/mod.rs
    //- mod1/foo.rs
        pub fn func(foo: crate::mod1::Foo) {
            let crate::mod1::Foo { field1, .. } = foo;
        }
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        pub struct Foo {
            pub field1: i32,
            field2: i32,
        }
    //- mod2/mod.rs
        pub mod foo;
    //- mod2/foo.rs
        pub fn func(foo: crate::mod1::Foo) {
            let crate::mod1::Foo { field1, .. } = foo;
        }
    """)

    fun `test destructuring struct with private field using type alias`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1 {
                pub mod foo;
                pub struct Foo { x: i32 }
                pub type Bar = Foo;
            }
            mod mod2 {}
        //- mod1/foo.rs
            pub fn func(foo: crate::mod1::Bar) {
                let crate::mod1::Bar { x } = foo;
            }
        """)
    }

    fun `test destructuring tuple struct with private field`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2",
            """
        //- main.rs
            mod mod1 {
                pub mod foo;
                pub struct Foo(i32);  // private field
            }
            mod mod2 {}
        //- mod1/foo.rs
            pub fn func(foo: crate::mod1::Foo) {
                let crate::mod1::Foo(x) = foo;
            }
        """)
    }

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test inside reference, when new parent module is private`() = expectConflicts {
        doTestExpectError(
            arrayOf("mod1/foo.rs"),
            "mod2/mod2_inner",
            """
        //- main.rs
            mod mod1;
            mod mod2;
        //- mod1/mod.rs
            mod foo;
            use foo::func;
        //- mod2/mod.rs
            mod mod2_inner;  // private
        //- mod2/mod2_inner/mod.rs
        //- mod1/foo.rs
            pub fn func() {}
        """)
    }

    fun `test add pub to moved file mod-declaration if necessary 1`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo;
        mod bar {
            use super::foo;
        }
    //- mod2/mod.rs
    //- mod1/foo.rs
        fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod bar {
            use crate::mod2::foo;
        }
    //- mod2/mod.rs
        pub mod foo;
    //- mod2/foo.rs
        fn func() {}
    """)

    fun `test add pub to moved file mod-declaration if necessary 2`() = doTest(
        "mod1/foo.rs",
        ".",
        """
    //- main.rs
        mod mod1;
    //- mod1/mod.rs
        mod foo;
        mod bar {
            use super::foo;
        }
    //- mod1/foo.rs
        fn func() {}
    """, """
    //- main.rs
        mod mod1;
        mod foo;
    //- mod1/mod.rs
        mod bar {
            use crate::foo;
        }
    //- foo.rs
        fn func() {}
    """)

    fun `test change scope for pub(in) visibility`() = doTest(
        "inner1/inner2/inner3/mod1/foo.rs",
        "inner1/inner2/mod2",
        """
    //- main.rs
        mod inner1 {
            mod inner2 {
                mod inner3 {
                    mod mod1;
                }
                mod mod2;
            }
        }
    //- inner1/inner2/inner3/mod1/mod.rs
        pub(super) mod foo;
    //- inner1/inner2/mod2/mod.rs
    //- inner1/inner2/inner3/mod1/foo.rs
        pub(crate) fn func1() {}
        pub(self) fn func2() {}
        pub(super) fn func3() {}
        pub(in super::super) fn func4() {}
        pub(in crate::inner1) fn func5() {}
    """, """
    //- main.rs
        mod inner1 {
            mod inner2 {
                mod inner3 {
                    mod mod1;
                }
                mod mod2;
            }
        }
    //- inner1/inner2/inner3/mod1/mod.rs
    //- inner1/inner2/mod2/mod.rs
        pub(in crate::inner1::inner2) mod foo;
    //- inner1/inner2/mod2/foo.rs
        pub(crate) fn func1() {}
        pub(self) fn func2() {}
        pub(in crate::inner1::inner2) fn func3() {}
        pub(in crate::inner1::inner2) fn func4() {}
        pub(in crate::inner1) fn func5() {}
    """)

    fun `test no visibility conflicts when move to inner directory`() = doTest(
        "foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod2;
        mod foo;
    //- mod2/mod.rs
    //- foo.rs
        struct A { field: u32 }

        pub fn func() {
            let a = A { field: 1 };
            let x = a.field;
        }
    """, """
    //- main.rs
        mod mod2;
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo.rs
        struct A { field: u32 }

        pub fn func() {
            let a = A { field: 1 };
            let x = a.field;
        }
    """)

    fun `test no visibility conflicts when moved file use self items`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod foo;
    //- mod2/mod.rs
    //- mod1/foo.rs
        struct Foo1(u32);
        struct Foo2 { field: u32 }
        fn func() {}

        fn test() {
            func();
            let foo1 = Foo1(0);
            let Foo1(x) = foo1;
            let foo2 = Foo2 { field: 0 };
            let Foo2 { field } = foo2;
        }
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo.rs
        struct Foo1(u32);
        struct Foo2 { field: u32 }
        fn func() {}

        fn test() {
            func();
            let foo1 = Foo1(0);
            let Foo1(x) = foo1;
            let foo2 = Foo2 { field: 0 };
            let Foo2 { field } = foo2;
        }
    """)
}
