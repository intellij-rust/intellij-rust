/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class RemoveRedundantFunctionArgumentsFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test no parameters`() = checkFixByText("Remove redundant arguments", """
        fn foo() {}

        fn main() {
            foo<error>(<error>1/*caret*/</error>)</error>;
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
            foo<error>(1, <error>2/*caret*/</error>)</error>;
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
            foo<error>(<error>1/*caret*/</error>, <error>2</error>)</error>;
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
            foo<error>(0, <error>1/*caret*/</error>, <error>2</error>)</error>;
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
            s.foo<error>(<error>1/*caret*/</error>)</error>;
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
            foo<error>(0, <error>1/*caret*/</error>)</error>;
        }
    """, """
        fn main() {
            let foo = |x| x + 1;
            foo(0);
        }
    """)
}
