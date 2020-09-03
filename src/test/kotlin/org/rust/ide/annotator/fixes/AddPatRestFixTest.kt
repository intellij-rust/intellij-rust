/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class AddPatRestFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test struct with fields and trailing comma`() = checkFixByText("Add '..'", """
        struct S {
            a: u32,
            b: f64,
            c: bool
        }
        fn main() {
            let s = S { a: 1, b: 2.4, c: false };
            let <error>S { a, }/*caret*/</error> = s;
        }
        """, """
        struct S {
            a: u32,
            b: f64,
            c: bool
        }
        fn main() {
            let s = S { a: 1, b: 2.4, c: false };
            let S { a, .. }/*caret*/ = s;
        }
        """
    )

    fun `test struct with fields and no training comma`() = checkFixByText("Add '..'", """
        struct S {
            a: u32,
            b: f64,
            c: bool
        }
        fn main() {
            let s = S { a: 1, b: 2.4, c: false };
            let <error>S { a }/*caret*/</error> = s;
        }
        """, """
        struct S {
            a: u32,
            b: f64,
            c: bool
        }
        fn main() {
            let s = S { a: 1, b: 2.4, c: false };
            let S { a, .. }/*caret*/ = s;
        }
        """
    )

    fun `test struct with no fields`() = checkFixByText("Add '..'", """
        struct S {
            a: u32,
            b: f64,
            c: bool
        }
        fn main() {
            let s = S { a: 1, b: 2.4, c: false };
            let <error>S {}/*caret*/</error> = s;
        }
        """, """
        struct S {
            a: u32,
            b: f64,
            c: bool
        }
        fn main() {
            let s = S { a: 1, b: 2.4, c: false };
            let S { .. }/*caret*/ = s;
        }
        """
    )

    fun `test tuple with fields and trailing comma`() = checkFixByText("Add '..'", """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let bar = Foo::Bar(1, 2, 3);
            let <error>Foo::Bar(a,)/*caret*/</error> = bar;
        }
        """, """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let bar = Foo::Bar(1, 2, 3);
            let Foo::Bar(a, ..) = bar;
        }
        """
    )

    fun `test tuple with fields and no training comma`() = checkFixByText("Add '..'", """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let bar = Foo::Bar(1, 2, 3);
            let <error>Foo::Bar(a)/*caret*/</error> = bar;
        }
        """, """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let bar = Foo::Bar(1, 2, 3);
            let Foo::Bar(a, ..) = bar;
        }
        """
    )

    fun `test test tuple with no fields`() = checkFixByText("Add '..'", """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let bar = Foo::Bar(1, 2, 3);
            let <error>Foo::Bar()/*caret*/</error> = bar;
        }
        """, """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let bar = Foo::Bar(1, 2, 3);
            let Foo::Bar(..) = bar;
        }
        """
    )
}
