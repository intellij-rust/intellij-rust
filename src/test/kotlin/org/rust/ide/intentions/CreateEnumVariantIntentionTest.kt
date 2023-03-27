/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ide.intentions.createFromUsage.CreateEnumVariantIntention

class CreateEnumVariantIntentionTest : RsIntentionTestBase(CreateEnumVariantIntention::class) {

    override val previewExpected: Boolean get() = false

    fun `test availability range`() = checkAvailableInSelectionOnly("""
        enum E {}
        fn main() {
            E::<selection>A</selection>;
        }
    """)

    fun `test simple variant, last existing variant has comma`() = doAvailableTest("""
        enum E {
            A,
        }
        fn main() {
            E::/*caret*/B;
        }
    """, """
        enum E {
            A,
            B,
        }
        fn main() {
            E::B;
        }
    """)

    fun `test simple variant, last existing variant doesn't have comma`() = doAvailableTest("""
        enum E {
            A
        }
        fn main() {
            E::/*caret*/B;
        }
    """, """
        enum E {
            A,
            B,
        }
        fn main() {
            E::B;
        }
    """)

    fun `test simple variant in empty enum`() = doAvailableTest("""
        enum E {}
        fn main() {
            E::/*caret*/A;
        }
    """, """
        enum E {
            A,
        }
        fn main() {
            E::A;
        }
    """)

    fun `test tuple-like variant`() = doAvailableTest("""
        enum E {
            A,
        }
        fn main() {
            E::/*caret*/B(1, 2);
        }
    """, """
        enum E {
            A,
            B(i32, i32),
        }
        fn main() {
            E::B(1, 2);
        }
    """)

    fun `test struct-like variant`() = doAvailableTest("""
        enum E {
            A,
        }
        fn main() {
            E::/*caret*/B { x: 0, y: 0 };
        }
    """, """
        enum E {
            A,
            B { x: i32, y: i32 },
        }
        fn main() {
            E::B { x: 0, y: 0 };
        }
    """)

    fun `test variant with this name already exists`() = doUnavailableTest("""
        enum E {
            A,
        }
        fn main() {
            E::/*caret*/A;
        }
    """)

    fun `test lowercase variant`() = doUnavailableTest("""
        enum E {}
        fn main() {
            E::/*caret*/new;
        }
    """)
}
