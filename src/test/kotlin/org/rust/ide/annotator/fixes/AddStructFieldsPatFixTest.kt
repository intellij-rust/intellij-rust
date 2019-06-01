/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class AddStructFieldsPatFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class.java) {
    fun `test one field missing`() = checkFixByText("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }
        fn main() {
            let <error>Foo { a, b }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }
        fn main() {
            let Foo { a, b, c }/*caret*/ = foo;
        }
        """
    )

    fun `test one field missing with trailing comma`() = checkFixByText("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }
        fn main() {
            let <error>Foo { a, b, }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }
        fn main() {
            let Foo { a, b, c, }/*caret*/ = foo;
        }
        """
    )

    fun `test missing fields with extra fields existing`() = checkFixByText("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let <error>Foo { a, <error>bar</error>, d }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let Foo { a, bar, b, c, d }/*caret*/ = foo;
        }
        """
    )

    fun `test filling order between 2 last fields existing`() = checkFixByText("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let <error>Foo { b, d }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let Foo { a, b, c, d }/*caret*/ = foo;
        }
        """
    )

    fun `test filling order between first and last fields existing`() = checkFixByText("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let <error>Foo { a, d }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let Foo { a, b, c, d }/*caret*/ = foo;
        }
        """
    )

    fun `test filling order with the first field existing`() = checkFixByText("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let <error>Foo { a }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let Foo { a, b, c, d }/*caret*/ = foo;
        }
        """
    )

    fun `test filling order with the second field existing`() = checkFixByText("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let <error>Foo { b }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let Foo { a, b, c, d }/*caret*/ = foo;
        }
        """
    )

    fun `test filling order with the third field existing`() = checkFixByText("Add missing fields", """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let <error>Foo { c }/*caret*/</error> = foo;
        }
        """, """
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
            d: i32,
        }
        fn main() {
            let Foo { a, b, c, d }/*caret*/ = foo;
        }
        """
    )

    fun `test one field missing in tuple`() = checkFixByText("Add missing fields", """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let x = Foo::Bar(1, 2, 3);
            match x {
                <error>Foo::Bar(a, b)/*caret*/</error> => {}
            }
        }
        """, """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let x = Foo::Bar(1, 2, 3);
            match x {
                Foo::Bar(a, b, _0/*caret*/) => {}
            }
        }
        """
    )

    fun `test tuple with no fields`() = checkFixByText("Add missing fields", """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let x = Foo::Bar(1, 2, 3);
            match x {
                <error>Foo::Bar()/*caret*/</error> => {}
            }
        }
        """, """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let x = Foo::Bar(1, 2, 3);
            match x {
                Foo::Bar(_0/*caret*/, _1, _2) => {}
            }
        }
        """
    )

    fun `test tuple with one field with comma`() = checkFixByText("Add missing fields", """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let x = Foo::Bar(1, 2, 3);
            match x {
                <error>Foo::Bar(a,)/*caret*/</error> => {}
            }
        }
        """, """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let x = Foo::Bar(1, 2, 3);
            match x {
                Foo::Bar(a, _0/*caret*/, _1, ) => {}
            }
        }
        """
    )

    fun `test tuple with one field without comma`() = checkFixByText("Add missing fields", """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let x = Foo::Bar(1, 2, 3);
            match x {
                <error>Foo::Bar(a)/*caret*/</error> => {}
            }
        }
        """, """
        enum Foo {
            Bar(i32, i32, i32)
        }
        fn main() {
            let x = Foo::Bar(1, 2, 3);
            match x {
                Foo::Bar(a, _0/*caret*/, _1) => {}
            }
        }
        """
    )
}
