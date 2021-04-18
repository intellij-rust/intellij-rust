/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ide.intentions.createFromUsage.CreateTupleStructIntention

class CreateTupleStructIntentionTest : RsIntentionTestBase(CreateTupleStructIntention::class) {
    fun `test tuple struct availability range`() = checkAvailableInSelectionOnly("""
        fn main() {
            <selection>Foo</selection>(0);
        }
    """)

    fun `test require camel case`() = doUnavailableTest("""
        fn main() {
            foo/*caret*/(0);
        }
    """)

    fun `test different expected type`() = doUnavailableTest("""
        fn main() {
            let x: u32 = Foo/*caret*/(0);
        }
    """)

    fun `test simple`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/(0);
        }
    """, """
        struct Foo(i32);

        fn main() {
            Foo(0);
        }
    """)

    fun `test no fields`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/();
        }
    """, """
        struct Foo();

        fn main() {
            Foo();
        }
    """)

    fun `test multiple fields`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/(true, "foo");
        }
    """, """
        struct Foo(bool, &'static str);

        fn main() {
            Foo(true, "foo");
        }
    """)

    fun `test unknown type`() = doAvailableTest("""
        fn main() {
            Foo/*caret*/(Bar);
        }
    """, """
        struct Foo(_);

        fn main() {
            Foo(Bar);
        }
    """)

    fun `test create in a module`() = doAvailableTest("""
        mod foo {}

        fn main() {
            foo::Foo/*caret*/(0);
        }
    """, """
        mod foo { pub(crate) struct Foo(pub(crate) i32); }

        fn main() {
            foo::Foo(0);
        }
    """)
}
