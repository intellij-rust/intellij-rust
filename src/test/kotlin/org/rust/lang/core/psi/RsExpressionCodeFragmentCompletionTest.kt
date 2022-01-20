/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

class RsExpressionCodeFragmentCompletionTest
    : RsCodeFragmentCompletionTestBase(::RsExpressionCodeFragment) {

    fun `test complete public field`() = checkContainsCompletion("""
        mod my {
            pub struct Foo { pub inner: i32 }
        }
        fn bar(foo: my::Foo) {
            /*caret*/;
        }
    """, "foo.in<caret>", "inner")

    fun `test do not complete private field`() = checkNotContainsCompletion("""
        mod my {
            pub struct Foo { inner: i32 }
        }
        fn bar(foo: my::Foo) {
            /*caret*/;
        }
    """, "foo.in<caret>", "inner")

    fun `test complete public function`() = checkContainsCompletion("""
        mod my {
            pub fn foobar() {}
        }
        fn bar() {
            use my::*;
            /*caret*/;
        }
    """, "foo<caret>", "foobar")

    fun `test do not complete private function`() = checkNotContainsCompletion("""
        mod my {
            fn foobar() {}
        }
        fn bar() {
            use my::*;
            /*caret*/;
        }
    """, "foo<caret>", "foobar")
}
