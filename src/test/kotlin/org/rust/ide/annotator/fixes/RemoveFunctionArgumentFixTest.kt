/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.SkipTestWrapping
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class RemoveRedundantFunctionArgumentsFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test availability range`() = checkFixAvailableInSelectionOnly("Remove redundant arguments", """
        fn foo() {}

        fn main() {
            foo<selection>(<error>1, 2</error>)</selection>;
        }
    """)

    fun `test no parameters`() = checkFixByText("Remove redundant arguments", """
        fn foo() {}

        fn main() {
            foo(<error>1/*caret*/</error>);
        }
    """, """
        fn foo() {}

        fn main() {
            foo();
        }
    """)

    fun `test single parameter`() = checkFixByText("Remove redundant arguments", """
        fn foo(a: u32) {}

        fn main() {
            foo(1, <error>2/*caret*/</error>);
        }
    """, """
        fn foo(a: u32) {}

        fn main() {
            foo(1);
        }
    """)

    fun `test multiple redundant arguments 1`() = checkFixByText("Remove redundant arguments", """
        fn foo() {}

        fn main() {
            foo(<error>1/*caret*/, 2</error>);
        }
    """, """
        fn foo() {}

        fn main() {
            foo();
        }
    """)

    fun `test multiple redundant arguments 2`() = checkFixByText("Remove redundant arguments", """
        fn foo(a: u32) {}

        fn main() {
            foo(0, <error>1/*caret*/, 2</error>);
        }
    """, """
        fn foo(a: u32) {}

        fn main() {
            foo(0);
        }
    """)

    fun `test method call`() = checkFixByText("Remove redundant arguments", """
        struct S;
        impl S {
            fn foo(&self) {}
        }

        fn foo(s: S) {
            s.foo(<error>1/*caret*/</error>);
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {}
        }

        fn foo(s: S) {
            s.foo();
        }
    """)

    fun `test lambda`() = checkFixByText("Remove redundant arguments", """
        fn main() {
            let foo = |x| x + 1;
            foo(0, <error>1/*caret*/</error>);
        }
    """, """
        fn main() {
            let foo = |x| x + 1;
            foo(0);
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/7830
    @SkipTestWrapping
    fun `test avoid infinite loop with syntax error`() = checkFixByText("Remove redundant arguments", """
        fn foo(a: &u32) {}

        fn main() {
            let a = 1;
            foo(&<error>'/*caret*/</error>static<error> </error><error>a</error>);
        }
    """, """
        fn foo(a: &u32) {}

        fn main() {
            let a = 1;
            foo(&'static);
        }
    """)
}
