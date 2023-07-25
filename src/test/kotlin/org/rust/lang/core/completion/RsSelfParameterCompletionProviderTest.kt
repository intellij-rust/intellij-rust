/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsSelfParameterCompletionProviderTest: RsCompletionTestBase() {
    fun `test complete self in method`() = doFirstCompletion("""
        impl Foo {
            fn f(se/*caret*/){}
        }
    """, """
        impl Foo {
            fn f(self/*caret*/){}
        }
    """)

    fun `test complete self as path in non-self parameter`() = doFirstCompletion("""
        impl Foo {
            fn f(foo: u8, se/*caret*/){}
        }
    """, """
        impl Foo {
            fn f(foo: u8, self::/*caret*/){}
        }
    """)

    fun `test complete self as path in nested function`() = doFirstCompletion("""
        impl Foo {
            fn f(){
                fn nested(se/*caret*/){}
            }
        }
    """, """
        impl Foo {
            fn f(){
                fn nested(self::/*caret*/){}
            }
        }
    """)

    fun `test complete self as path in generic argument`() = doFirstCompletion("""
        impl Foo {
            fn f(x: Box<se/*caret*/>){}
        }
    """, """
        impl Foo {
            fn f(x: Box<self::/*caret*/>){}
        }
    """)

    fun `test complete self as path in pat generic argument`() = doFirstCompletion("""
        impl Foo {
            fn f(Box<se/*caret*/>){}
        }
    """, """
        impl Foo {
            fn f(Box<self::/*caret*/>){}
        }
    """)

    fun `test complete self in reference`() = doFirstCompletion("""
        impl Foo {
            fn f(&se/*caret*/){}
        }
    """, """
        impl Foo {
            fn f(&self/*caret*/){}
        }
    """)

    fun `test complete self in mut reference`() = doFirstCompletion("""
        impl Foo {
            fn f(&mut se/*caret*/){}
        }
    """, """
        impl Foo {
            fn f(&mut self/*caret*/){}
        }
    """)

    fun `test complete self as path if self exists`() = doFirstCompletion("""
        impl Foo {
            fn f(self, se/*caret*/){}
        }
    """, """
        impl Foo {
            fn f(self, self::/*caret*/){}
        }
    """)

    fun `test complete self as path in regular function`() = doFirstCompletion("""
        fn f(se/*caret*/){}
    """, """
        fn f(self::/*caret*/){}
    """)
}
