/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class RemoveFunctionArgumentFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test no parameters`() = checkFixByText("Remove argument", """
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

    fun `test single parameter`() = checkFixByText("Remove argument", """
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

    fun `test multiple redundant arguments`() = checkFixByText("Remove argument", """
        fn foo() {}

        fn main() {
            foo(<error>1/*caret*/</error>, <error>2</error>);
        }
    """, """
        fn foo() {}

        fn main() {
            foo(2);
        }
    """)

    fun `test keep whitespace and comments`() = checkFixByText("Remove argument", """
        fn foo() {}

        fn main() {
            foo(<error>1/*caret*/</error> /* there is a comment here */ );
        }
    """, """
        fn foo() {}

        fn main() {
            foo(/* there is a comment here */ );
        }
    """)

    fun `test method call`() = checkFixByText("Remove argument", """
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

    fun `test lambda`() = checkFixByText("Remove argument", """
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
}
