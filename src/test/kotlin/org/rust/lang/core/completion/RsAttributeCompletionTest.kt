/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsAttributeCompletionTest : RsCompletionTestBase() {
    fun `test derive on struct`() = doSingleCompletion("""
        #[der/*caret*/]
        struct Bar;
    """, """
        #[derive(/*caret*/)]
        struct Bar;
    """)

    fun `test warn on trait`() = doSingleCompletion("""
        #[war/*caret*/]
        trait Bar {}
    """, """
        #[warn(/*caret*/)]
        trait Bar {}
    """)

    fun `test inline on fn`() = doSingleCompletion("""
        #[inl/*caret*/]
        fn foo() {}
    """, """
        #[inline/*caret*/]
        fn foo() {}
    """)

    fun `test allow on fn`() = doSingleCompletion("""
        #[all/*caret*/]
        fn foo() {}
    """, """
        #[allow(/*caret*/)]
        fn foo() {}
    """)

    fun `test simd on tuple struct`() = doSingleCompletion("""
        #[si/*caret*/]
        struct Bar(u8, u8);
    """, """
        #[simd/*caret*/]
        struct Bar(u8, u8);
    """)

    fun `test allow on static`() = doSingleCompletion("""
        #[allo/*caret*/]
        static BAR: u8 = 1;
    """, """
        #[allow(/*caret*/)]
        static BAR: u8 = 1;
    """)

    fun `test thread local on static mut`() = doSingleCompletion("""
        #[thre/*caret*/]
        static mut BAR: u8 = 1;
    """, """
        #[thread_local/*caret*/]
        static mut BAR: u8 = 1;
    """)

    fun `test deny on enum`() = doSingleCompletion("""
        #[den/*caret*/]
        enum Foo {
            BAR,
            BAZ
        }
    """, """
        #[deny(/*caret*/)]
        enum Foo {
            BAR,
            BAZ
        }
    """)

    fun `test no mangle on enum`() = doSingleCompletion("""
        #[no_ma/*caret*/]
        mod foo {}
    """, """
        #[no_mangle/*caret*/]
        mod foo {}
    """)

    fun `test outer deny on file`() = doSingleCompletion("""
        #![den/*caret*/]
    """, """
        #![deny(/*caret*/)]
    """)

    fun `test macro use on mod`() = doSingleCompletion("""
        #[macr/*caret*/]
        mod foo {}
    """, """
        #[macro_use/*caret*/]
        mod foo {}
    """)

    fun `test macro use on mod 2`() = doSingleCompletion("""
        #[macr/*caret*/]
        mod foo;
    """, """
        #[macro_use/*caret*/]
        mod foo;
    """)

    fun `test outer warn on mod`() = doSingleCompletion("""
        mod foo {
            #![war/*caret*/]
        }
    """, """
        mod foo {
            #![warn(/*caret*/)]
        }
    """)

    fun `test export name on trait impl method`() = doSingleCompletion("""
        struct HasDrop;
        impl Drop for HasDrop {
            #[allo/*caret*/]
            fn drop(&mut self) {}
        }
    """, """
        struct HasDrop;
        impl Drop for HasDrop {
            #[allow(/*caret*/)]
            fn drop(&mut self) {}
        }
    """)

    fun `test linked from on extern block`() = doSingleCompletion("""
        #[linke/*caret*/]
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """, """
        #[linked_from/*caret*/]
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun `test linkage on extern block decl`() = doSingleCompletion("""
        extern {
            #[linka/*caret*/]
            fn bar(baz: size_t) -> size_t;
        }
    """, """
        extern {
            #[linkage/*caret*/]
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun `test no link on extern crate`() = doSingleCompletion("""
        #[no_l/*caret*/]
        extern crate bar;
    """, """
        #[no_link/*caret*/]
        extern crate bar;
    """)

    fun `test macro export on macro`() = doSingleCompletion("""
        #[macr/*caret*/]
        macro_rules! bar {}
    """, """
        #[macro_export/*caret*/]
        macro_rules! bar {}
    """)

    fun `test cfg on mod`() = checkContainsCompletion("cfg", """
        #[cf/*caret*/]
        mod foo {}
    """)

    fun `test cfg on file inner`() = checkContainsCompletion("cfg", """
        #![cf/*caret*/]
    """)

    fun `test deprecated`() = doSingleCompletion("""
        #[dep/*caret*/]
        mod foo {}
    """, """
        #[deprecated/*caret*/]
        mod foo {}
    """)

    fun `test do not complete existing attributes`() = checkNoCompletion("""
        #[deprecated]
        #[dep/*caret*/]
        mod foo {}
    """)
}
