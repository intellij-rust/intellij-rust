/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class DerefPostfixTemplateTest : RsPostfixTemplateTest(DerefPostfixTemplate(RsPostfixTemplateProvider())) {
    fun `test simple`() = doTest("""
        fn main() {
            let v = &123;
            assert_eq!(123, v.deref/*caret*/);
        }
    """, """
        fn main() {
            let v = &123;
            assert_eq!(123, *v);
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test deref trait`() = doTest("""
        struct Foo<T>(T);
        impl<T> std::ops::Deref for Foo<T> {
            type Target = T;
            fn deref(&self) -> &T {
                &self.0
            }
        }
        fn main() {
            let foo = Foo('x');
            assert_eq!('x', foo.deref/*caret*/);
        }
    """, """
        struct Foo<T>(T);
        impl<T> std::ops::Deref for Foo<T> {
            type Target = T;
            fn deref(&self) -> &T {
                &self.0
            }
        }
        fn main() {
            let foo = Foo('x');
            assert_eq!('x', *foo/*caret*/);
        }
    """)

    fun `test deref raw pointer`() = doTest("""
        fn main() {
            let ptr = &123 as *const i32;
            unsafe {
                ptr.deref/*caret*/;
            }
        }
    """, """
        fn main() {
            let ptr = &123 as *const i32;
            unsafe {
                *ptr;
            }
        }
    """)

    fun `test no deref for non reference`() = doTestNotApplicable("""
        fn main() {
            assert_eq!(123, 123.deref/*caret*/);
        }
    """)

    fun `test no deref in string literal`() = doTestNotApplicable("""
        fn main() {
            "qwe.deref/*caret*/";
        }
    """)

    fun `test no deref in raw string literal`() = doTestNotApplicable("""
        fn main() {
            r#"qwe.deref/*caret*/"#;
        }
    """)

    fun `test no deref in byte string literal`() = doTestNotApplicable("""
        fn main() {
            b"qwe.deref/*caret*/";
        }
    """)

    fun `test no deref in raw byte string literal`() = doTestNotApplicable("""
        fn main() {
            br#"qwe.deref/*caret*/"#;
        }
    """)
}
