/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class WrapTypePathPostfixTemplateTest : RsPostfixTemplateTest(WrapTypePathPostfixTemplate::class) {
    fun `test ignore path expression`() = doTestNotApplicable("""
        struct S;

        fn main() {
            let a = S.wrap/*caret*/
        }
    """)

    fun `test simple path`() = doTestWithLiveTemplate("""
        fn foo(a: u32.wrap/*caret*/) {}
    """, "Wrapper\t", """
        fn foo(a: Wrapper<u32>/*caret*/) {}
    """)

    fun `test path with type arguments`() = doTestWithLiveTemplate("""
        struct S<T>(T);

        fn foo(a: S<u32>.wrap/*caret*/) {}
    """, "Wrapper\t", """
        struct S<T>(T);

        fn foo(a: Wrapper<S<u32>>/*caret*/) {}
    """)

    fun `test qualified path`() = doTestWithLiveTemplate("""
        mod bar {
            pub struct S;
        }

        fn foo(a: bar::S.wrap/*caret*/) {}
    """, "Wrapper\t", """
        mod bar {
            pub struct S;
        }

        fn foo(a: Wrapper<bar::S>/*caret*/) {}
    """)

    fun `test nested path`() = doTestWithLiveTemplate("""
        struct S<T>(T);

        fn foo(a: S<u32.wrap/*caret*/>) {}
    """, "Wrapper\t", """
        struct S<T>(T);

        fn foo(a: S<Wrapper<u32>/*caret*/>) {}
    """)

    fun `test tuple type`() = doTestWithLiveTemplate("""
        fn foo(a: (u32, u32).wrap/*caret*/) {}
    """, "Wrapper\t", """
        fn foo(a: Wrapper<(u32, u32)>/*caret*/) {}
    """)
}
