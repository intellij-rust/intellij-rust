/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddDeriveIntentionTest : RsIntentionTestBase(AddDeriveIntention::class) {
    fun `test availability range in struct`() = checkAvailableInSelectionOnly("""
        <selection>struct Test {
            field: i32
        }</selection>
    """)

    fun `test availability range in tuple struct`() = checkAvailableInSelectionOnly("""
        <selection>struct Test (i32);</selection>
    """)

    fun `test availability range in enum`() = checkAvailableInSelectionOnly("""
        <selection>enum Test {
            Something
        }</selection>
    """)

    fun `test add derive struct`() = doAvailableTest("""
        struct Test/*caret*/ {}
    """, """
        #[derive(/*caret*/)]
        struct Test {}
    """)

    fun `test add derive pub struct`() = doAvailableTest("""
        pub struct Test/*caret*/ {}
    """, """
        #[derive(/*caret*/)]
        pub struct Test {}
    """)

    fun `test add derive enum`() = doAvailableTest("""
        enum Test /*caret*/{
            Something
        }
    """, """
        #[derive(/*caret*/)]
        enum Test {
            Something
        }
    """)

    fun `test add derive existing attr`() = doAvailableTest("""
        #[derive(Something)]
        struct Test/*caret*/ {}
    """, """
        #[derive(Something/*caret*/)]
        struct Test {}
    """)
}
