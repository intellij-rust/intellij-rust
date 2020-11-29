/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsAttributeCompletionTest : RsAttributeCompletionTestBase() {
    fun `test derive on struct`() = doSingleAttributeCompletion("""
        #[der/*caret*/]
        struct Bar;
    """, """
        #[derive(/*caret*/)]
        struct Bar;
    """)

    fun `test warn on trait`() = doSingleAttributeCompletion("""
        #[war/*caret*/]
        trait Bar {}
    """, """
        #[warn(/*caret*/)]
        trait Bar {}
    """)

    fun `test inline on fn`() = doSingleAttributeCompletion("""
        #[inl/*caret*/]
        fn foo() {}
    """, """
        #[inline/*caret*/]
        fn foo() {}
    """)

    fun `test outer allow on fn`() = doSingleAttributeCompletion("""
        #[allo/*caret*/]
        fn foo() {}
    """, """
        #[allow(/*caret*/)]
        fn foo() {}
    """)

    fun `test inner allow on fn`() = doSingleAttributeCompletion("""
        fn foo() {
            #![allo/*caret*/]
        }
    """, """
        fn foo() {
            #![allow(/*caret*/)]
        }
    """)

    fun `test simd on tuple struct`() = doSingleAttributeCompletion("""
        #[si/*caret*/]
        struct Bar(u8, u8);
    """, """
        #[simd/*caret*/]
        struct Bar(u8, u8);
    """)

    fun `test allow on static`() = doSingleAttributeCompletion("""
        #[allo/*caret*/]
        static BAR: u8 = 1;
    """, """
        #[allow(/*caret*/)]
        static BAR: u8 = 1;
    """)

    fun `test thread local on static mut`() = doSingleAttributeCompletion("""
        #[thre/*caret*/]
        static mut BAR: u8 = 1;
    """, """
        #[thread_local/*caret*/]
        static mut BAR: u8 = 1;
    """)

    fun `test deny on enum`() = doSingleAttributeCompletion("""
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

    fun `test no mangle on enum`() = doSingleAttributeCompletion("""
        #[no_ma/*caret*/]
        mod foo {}
    """, """
        #[no_mangle/*caret*/]
        mod foo {}
    """)

    fun `test outer deny on file`() = doSingleAttributeCompletion("""
        #![den/*caret*/]
    """, """
        #![deny(/*caret*/)]
    """)

    fun `test macro use on mod`() = doSingleAttributeCompletion("""
        #[macr/*caret*/]
        mod foo {}
    """, """
        #[macro_use/*caret*/]
        mod foo {}
    """)

    fun `test macro use on mod 2`() = doSingleAttributeCompletion("""
        #[macr/*caret*/]
        mod foo;
    """, """
        #[macro_use/*caret*/]
        mod foo;
    """)

    fun `test outer warn on mod`() = doSingleAttributeCompletion("""
        mod foo {
            #![war/*caret*/]
        }
    """, """
        mod foo {
            #![warn(/*caret*/)]
        }
    """)

    fun `test export name on trait impl method`() = doSingleAttributeCompletion("""
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

    fun `test linked from on extern block`() = doSingleAttributeCompletion("""
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

    fun `test linkage on extern block decl`() = doSingleAttributeCompletion("""
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

    fun `test no link on extern crate`() = doSingleAttributeCompletion("""
        #[no_l/*caret*/]
        extern crate bar;
    """, """
        #[no_link/*caret*/]
        extern crate bar;
    """)

    fun `test macro export on macro`() = doSingleAttributeCompletion("""
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

    fun `test deprecated`() = doSingleAttributeCompletion("""
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

    fun `test repr completion on enum`() = doSingleAttributeCompletion("""
        #[rep/*caret*/]
        enum Foo {}
    """, """
        #[repr(/*caret*/)]
        enum Foo {}
    """)

    fun `test repr completion on struct`() = doSingleAttributeCompletion("""
        #[rep/*caret*/]
        struct Foo {}
    """, """
        #[repr(/*caret*/)]
        struct Foo {}
    """)

    fun `test track_caller on function`() = doSingleAttributeCompletion("""
        #[track/*caret*/]
        fn foo() {}
    """, """
        #[track_caller/*caret*/]
        fn foo() {}
    """)

    fun `test no track_caller on struct`() = checkNoCompletion("""
        #[track/*caret*/]
        struct Foo;
    """)

    fun `test non_exhaustive on enum`() = doSingleAttributeCompletion("""
        #[non_/*caret*/]
        enum Foo {}
    """, """
        #[non_exhaustive/*caret*/]
        enum Foo {}
    """)

    fun `test non_exhaustive on struct`() = doSingleAttributeCompletion("""
        #[non_/*caret*/]
        struct S {
            a: u32
        }
    """, """
        #[non_exhaustive/*caret*/]
        struct S {
            a: u32
        }
    """)

    fun `test non_exhaustive on enum variant`() = doSingleAttributeCompletion("""
        enum Foo {
            #[non_/*caret*/]
            A
        }
    """, """
        enum Foo {
            #[non_exhaustive/*caret*/]
            A
        }
    """)

    fun `test no non_exhaustive on function`() = checkNoCompletion("""
        #[non_/*caret*/]
        fn foo() {}
    """)
}
