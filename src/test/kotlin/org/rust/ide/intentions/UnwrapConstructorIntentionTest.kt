/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class UnwrapConstructorIntentionTest : RsIntentionTestBase(UnwrapConstructorIntention::class) {
    fun `test available inside the whole expression`() = checkAvailableInSelectionOnly("""
        struct Ok(u32);

        fn main() {
            <selection>Ok(1)</selection>;
        }
    """)

    fun `test unavailable for function from argument`() = doUnavailableTest("""
        fn foo(x: u32) {}

        fn main() {
            foo/*caret*/(1);
        }
    """)

    fun `test unavailable for multiple arguments`() = doUnavailableTest("""
        struct Ok(u32, u32);

        fn main() {
            Ok/*caret*/(1, 2);
        }
    """)

    fun `test tuple struct`() = doAvailableTest("""
        struct Ok(u32);

        fn main() {
            Ok/*caret*/(1);
        }
    """, """
        struct Ok(u32);

        fn main() {
            1/*caret*/;
        }
    """)

    fun `test enum variant`() = doAvailableTest("""
        enum Option {
            Some(u32),
            None
        }

        fn main() {
            Option::Some(1/*caret*/);
        }
    """, """
        enum Option {
            Some(u32),
            None
        }

        fn main() {
            1/*caret*/;
        }
    """)
}
