/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsVisRestrictionCompletionTest : RsCompletionTestBase() {
    fun `test complete vis restriction without in keyword`() = checkContainsCompletion(listOf("super", "crate", "self", "in "), """
        pub mod foo {
            pub(/*caret*/) struct S;
        }
    """)

    fun `test complete vis restriction with in keyword`() = checkNotContainsCompletion(listOf("in "), """
        pub mod foo {
            pub(in /*caret*/) struct S;
        }
    """)

    fun `test complete in keyword`() = doFirstCompletion("""
        pub mod foo {
            pub(i/*caret*/) struct S;
        }
    """, """
        pub mod foo {
            pub(in /*caret*/) struct S;
        }
    """)

    fun `test do not add colon colon after module insertion`() = doFirstCompletion("""
        pub mod foo {
            pub(c/*caret*/) struct S;
        }
    """, """
        pub mod foo {
            pub(crate/*caret*/) struct S;
        }
    """)

    fun `test complete self without colon`() = doFirstCompletion("""
        pub mod foo {
            pub(in sel/*caret*/) struct S;
        }
    """, """
        pub mod foo {
            pub(in self/*caret*/) struct S;
        }
    """)

    fun `test complete super without colon`() = doFirstCompletion("""
        pub mod foo {
            pub(in sup/*caret*/) struct S;
        }
    """, """
        pub mod foo {
            pub(in super/*caret*/) struct S;
        }
    """)

    fun `test complete super without colon after self`() = checkCompletion("super", """
        pub mod foo {
            pub(in self::sup/*caret*/) struct S;
        }
    """, """
        pub mod foo {
            pub(in self::super/*caret*/) struct S;
        }
    """)

    fun `test do not complete general paths in vis restriction path without in`() = checkNoCompletion("""
        pub mod foo {
            pub(crate::f/*caret*/) struct S;
        }
    """)
}
