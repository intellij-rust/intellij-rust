/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class RsMoveLeftRightHandlerTest : RsTestBase() {

    fun `test function arguments`() = doRightLeftTest("""
        fn main() {
            foo("12/*caret*/3", 123);
        }
    """, """
        fn main() {
            foo(123, "12/*caret*/3");
        }
    """)

    fun `test function parameters`() = doRightLeftTest("""
        fn foo(str: /*caret*/String, int: i32) {}
    """, """
        fn foo(int: i32, str: /*caret*/String) {}
    """)

    fun `test don't move self parameter`() = doMoveRightTest("""
        impl S {
            fn foo(&/*caret*/self, str: String) {}
        }
    """, """
        impl S {
            fn foo(&/*caret*/self, str: String) {}
        }
    """)

    fun `test type parameters`() = doRightLeftTest("""
        fn foo<T1/*caret*/, T2>(p1: T1, p2: T2) {}
    """, """
        fn foo<T2, T1/*caret*/>(p1: T1, p2: T2) {}
    """)

    fun `test lifetime parameters`() = doRightLeftTest("""
        fn foo<'a/*caret*/, 'b>(p1: &'a str, p2: &'b str) {}
    """, """
        fn foo<'b, 'a/*caret*/>(p1: &'a str, p2: &'b str) {}
    """)

    fun `test lifetime and type parameters`() = doMoveRightTest("""
        fn foo<'a/*caret*/, T>(p1: &'a str, p2: T) {}
    """, """
        fn foo<T, 'a/*caret*/>(p1: &'a str, p2: T) {}
    """)

    fun `test type param bounds`() = doRightLeftTest("""
        fn foo<T: Ord/*caret*/ + Hash>(p: T) {}
    """, """
        fn foo<T: Hash + Ord/*caret*/>(p: T) {}
    """)

    fun `test lifetime param bounds`() = doRightLeftTest("""
        fn foo<'a, 'b, 'c>(i: &'a i32) where 'a: 'c/*caret*/ + 'b {}
    """, """
        fn foo<'a, 'b, 'c>(i: &'a i32) where 'a: 'b + 'c/*caret*/ {}
    """)

    fun `test array expr 1 `() = doRightLeftTest("""
        fn main() {
            let a = [1, 2/*caret*/, 3];
        }
    """, """
        fn main() {
            let a = [1, 3, 2/*caret*/];
        }
    """)

    fun `test array expr 2`() = doMoveRightTest("""
        fn main() {
            let a = [0/*caret*/; 2];
        }
    """, """
        fn main() {
            let a = [0/*caret*/; 2];
        }
    """)

    fun `test vec macro 1 `() = doRightLeftTest("""
        fn main() {
            let a = vec![1, 2/*caret*/, 3];
        }
    """, """
        fn main() {
            let a = vec![1, 3, 2/*caret*/];
        }
    """)

    fun `test vec macro 2`() = doMoveRightTest("""
        fn main() {
            let a = vec![0/*caret*/; 2];
        }
    """, """
        fn main() {
            let a = vec![0/*caret*/; 2];
        }
    """)

    fun `test tuple expr`() = doRightLeftTest("""
        fn main() {
            let a = (1, "foo"/*caret*/, 3);
        }
    """, """
        fn main() {
            let a = (1, 3, "foo"/*caret*/);
        }
    """)

    fun `test tuple type`() = doRightLeftTest("""
        fn foo() -> (i32/*caret*/, String) { !unimplemented() }
    """, """
        fn foo() -> (String, i32/*caret*/) { !unimplemented() }
    """)

    fun `test struct tuple fields`() = doRightLeftTest("""
        struct Foo(i32/*caret*/, String);
    """, """
        struct Foo(String, i32/*caret*/);
    """)

    fun `test attributes 1`() = doRightLeftTest("""
        #[derive(Copy/*caret*/, Clone)]
        struct Foo;
    """, """
        #[derive(Clone, Copy/*caret*/)]
        struct Foo;
    """)

    fun `test attributes 2`() = doRightLeftTest("""
        #[deprecated(note = /*caret*/"...", since = "0.10.0")]
        struct Foo;
    """, """
        #[deprecated(since = "0.10.0", note = /*caret*/"...")]
        struct Foo;
    """)

    fun `test use item`() = doRightLeftTest("""
        use std::collections::{HashMap/*caret*/, BinaryHeap};
    """, """
        use std::collections::{BinaryHeap, HashMap/*caret*/};
    """)

    fun `test format macros`() {
        for (macros in listOf("println", "info")) {
            doRightLeftTest("""
                fn main() {
                    !$macros("{} {}", 123/*caret*/, "foo");
                }
            """, """
                fn main() {
                    !$macros("{} {}", "foo", 123/*caret*/);
                }
            """)
        }
    }

    fun `test don't move target in log macros`() = doMoveRightTest("""
        fn main() {
            warn!(target: /*caret*/"foo", "warn log");
        }
    """, """
        fn main() {
            warn!(target: /*caret*/"foo", "warn log");
        }
    """)

    private fun doRightLeftTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        doMoveRightTest(before, after)
        doMoveLeftTest(after, before)
    }

    private fun doMoveLeftTest(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkByText(before, after) {
            myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
        }

    private fun doMoveRightTest(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkByText(before, after) {
            myFixture.performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT)
        }
}
