/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes


import org.rust.ide.inspections.RsBorrowCheckerInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class AddMutableFixText : RsInspectionsTestBase(RsBorrowCheckerInspection()) {
    fun `test add mutable fix to self`() = checkFixByText("Make `self` mutable", """
        struct A {}

        impl A {
            fn foo(&mut self) {  }

            fn bar (&self) {
                <error>self/*caret*/</error>.foo();
            }
        }
    """, """
        struct A {}

        impl A {
            fn foo(&mut self) {  }

            fn bar (&mut self) {
                self.foo();
            }
        }
    """)

    fun `test add mutable fix to self2`() = checkFixByText("Make `self` mutable", """
        struct A {}

        impl A {
            fn foo(&mut self) {  }
        }

        struct V {
            a: A
        }

        impl V {
            fn foo(&self) {
                <error>self.a/*caret*/</error>.foo()
            }
        }
    """, """
        struct A {}

        impl A {
            fn foo(&mut self) {  }
        }

        struct V {
            a: A
        }

        impl V {
            fn foo(&mut self) {
                self.a.foo()
            }
        }
    """)

    fun `test add mutable fix to parameter with lifetime`() = checkFixByText("Make `obj` mutable", """
        struct A {}

        impl A {
            fn foo(&mut self) {  }

            fn bar<'a>(&self, obj: &'a A) {
                <error>obj/*caret*/</error>.foo()
            }
        }
    """, """
        struct A {}

        impl A {
            fn foo(&mut self) {  }

            fn bar<'a>(&self, obj: &'a mut A) {
                obj.foo()
            }
        }
    """)
}
