/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.BatchMode
import org.rust.ide.colors.RsColor

class RsUnsafeExpressionHighlightingAnnotatorTest : RsAnnotatorTestBase(RsUnsafeExpressionAnnotator::class) {
    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(listOf(RsColor.UNSAFE_CODE.testSeverity))
    }

    fun `test extern static in unsafe block`() = checkHighlighting("""
        extern {
            static FOO: i32;
        }

        fn main() {
            unsafe { let a = <UNSAFE_CODE descr="Use of unsafe extern static">FOO</UNSAFE_CODE>; }
        }
    """)

    fun `test mutable static`() = checkHighlighting("""
        static mut FOO : u8 = 0;

        fn main() {
            unsafe { <UNSAFE_CODE descr="Use of unsafe mutable static">FOO</UNSAFE_CODE> += 1; }
        }
    """)

    fun `test mutable static with complex path`() = checkHighlighting("""
        mod foo {
            pub static mut FOO : u8 = 0;
        }

        fn main() {
            unsafe { foo::<UNSAFE_CODE descr="Use of unsafe mutable static">FOO</UNSAFE_CODE> += 1; }
        }
    """)

    fun `test unsafe call`() = checkHighlighting("""
        unsafe fn foo() {}
        unsafe fn bar() { <UNSAFE_CODE descr="Call to unsafe function">foo</UNSAFE_CODE>(); }
    """)

    fun `test unsafe call with complex path`() = checkHighlighting("""
        mod foo {
            unsafe fn foo<T>() {}
        }
        unsafe fn bar() { foo::<UNSAFE_CODE descr="Call to unsafe function">foo::<u8></UNSAFE_CODE>(); }
    """)

    fun `test unsafe method call`() = checkHighlighting("""
        struct S;
        impl S {
            unsafe fn foo(&self) {}
        }
        unsafe fn bar() { S.<UNSAFE_CODE descr="Call to unsafe function">foo</UNSAFE_CODE>(); }
    """)

    fun `test unsafe method call with type parameters`() = checkHighlighting("""
        struct S;
        impl S {
            unsafe fn foo<T>(&self) {}
        }
        unsafe fn bar() { S.<UNSAFE_CODE descr="Call to unsafe function">foo::<u8></UNSAFE_CODE>(); }
    """)

    fun `test raw pointer dereference in unsafe block`() = checkHighlighting("""
        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = unsafe { <UNSAFE_CODE descr="Unsafe dereference of raw pointer">*</UNSAFE_CODE>char_ptr };
        }
    """)

    fun `test raw pointer dereference in unsafe fn`() = checkHighlighting("""
        fn main() {
        }
        unsafe fn foo() {
            let char_ptr: *const char = 42 as *const _;
            let val = <UNSAFE_CODE descr="Unsafe dereference of raw pointer">*</UNSAFE_CODE>char_ptr;
        }
    """)

    @BatchMode
    fun `test no highlighting in batch mode`() = checkHighlighting("""
        struct S;
        impl S {
            unsafe fn foo(&self) {}
        }
        unsafe fn bar() { S.foo(); }
        static mut FOO : u8 = 0;

        fn main() {
            let char_ptr: *const char = 42 as *const _;
            let val = unsafe { *char_ptr };
            unsafe { FOO += 1; }
        }
    """, ignoreExtraHighlighting = false)
}
