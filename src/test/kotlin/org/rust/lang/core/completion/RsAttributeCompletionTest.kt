/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsAttributeCompletionTest : RsCompletionTestBase() {
    fun `test derive on struct`() = @Suppress("DEPRECATION") checkSingleCompletion("derive", """
        #[der/*caret*/]
        struct Bar;
    """)

    fun `test warn on trait`() = @Suppress("DEPRECATION") checkSingleCompletion("warn", """
        #[war/*caret*/]
        trait Bar {}
    """)

    fun `test inline on fn`() = @Suppress("DEPRECATION") checkSingleCompletion("inline", """
        #[inl/*caret*/]
        fn foo() {}
    """)

    fun `test allow on fn`() = @Suppress("DEPRECATION") checkSingleCompletion("allow", """
        #[all/*caret*/]
        fn foo() {}
    """)

    fun `test simd on tuple struct`() = @Suppress("DEPRECATION") checkSingleCompletion("simd", """
        #[si/*caret*/]
        struct Bar(u8, u8);
    """)

    fun `test allow on static`() = @Suppress("DEPRECATION") checkSingleCompletion("allow", """
        #[allo/*caret*/]
        static BAR: u8 = 1;
    """)

    fun `test thread local on static mut`() = @Suppress("DEPRECATION") checkSingleCompletion("thread_local", """
        #[thre/*caret*/]
        static mut BAR: u8 = 1;
    """)

    fun `test deny on enum`() = @Suppress("DEPRECATION") checkSingleCompletion("deny", """
        #[den/*caret*/]
        enum Foo {
            BAR,
            BAZ
        }
    """)

    fun `test no mangle on enum`() = @Suppress("DEPRECATION") checkSingleCompletion("no_mangle", """
        #[no_ma/*caret*/]
        mod foo {}
    """)

    fun `test outer deny on file`() = @Suppress("DEPRECATION") checkSingleCompletion("deny", """
        #![den/*caret*/]
    """)

    fun `test macro use on mod`() = @Suppress("DEPRECATION") checkSingleCompletion("macro_use", """
        #[macr/*caret*/]
        mod foo {}
    """)

    fun `test macro use on mod 2`() = doSingleCompletion("""
        #[macr/*caret*/]
        mod foo;
    """, """
        #[macro_use/*caret*/]
        mod foo;
    """)

    fun `test outer warn on mod`() = @Suppress("DEPRECATION") checkSingleCompletion("warn", """
        mod foo {
            #![war/*caret*/]
        }
    """)

    fun `test export name on trait impl method`() = @Suppress("DEPRECATION") checkSingleCompletion("allow", """
        struct HasDrop;
        impl Drop for HasDrop {
            #[allo/*caret*/]
            fn drop(&mut self) {}
        }
    """)

    fun `test linked from on extern block`() = @Suppress("DEPRECATION") checkSingleCompletion("linked_from", """
        #[linke/*caret*/]
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun `test linkage on extern block decl`() = @Suppress("DEPRECATION") checkSingleCompletion("linkage", """
        extern {
            #[linka/*caret*/]
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun `test no link on extern crate`() = @Suppress("DEPRECATION") checkSingleCompletion("no_link", """
        #[no_l/*caret*/]
        extern crate bar;
    """)

    fun `test macro export on macro`() = @Suppress("DEPRECATION") checkSingleCompletion("macro_export", """
        #[macr/*caret*/]
        macro_rules! bar {}
    """)

    fun `test cfg on mod`() = checkContainsCompletion("cfg", """
        #[cf/*caret*/]
        mod foo {}
    """)

    fun `test cfg on file inner`() = checkContainsCompletion("cfg", """
        #![cf/*caret*/]
    """)
}
