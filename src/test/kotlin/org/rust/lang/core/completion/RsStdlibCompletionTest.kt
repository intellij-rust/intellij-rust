/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.openapi.util.SystemInfo
import org.rust.ProjectDescriptor
import org.rust.WithActualStdlibRustProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.wsl.RsWslToolchain

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibCompletionTest : RsCompletionTestBase() {
    fun `test prelude`() = doFirstCompletion("""
        fn main() {
            dr/*caret*/
        }
    """, """
        fn main() {
            drop(/*caret*/)
        }
    """)

    fun `test prelude visibility`() = checkNoCompletion("""
        mod m {}
        fn main() {
            m::dr/*caret*/
        }
    """)

    fun `test iter`() = @Suppress("DEPRECATION") checkSingleCompletion("iter_mut()", """
        fn main() {
            let vec: Vec<i32> = Vec::new();
            let iter = vec.iter_mu/*caret*/
        }
    """)

    fun `test derived trait method`() = @Suppress("DEPRECATION") checkSingleCompletion("fmt", """
        #[derive(Debug)]
        struct Foo;
        fn bar(foo: Foo) {
            foo.fm/*caret*/
        }
    """)

    fun `test macro`() = doSingleCompletion("""
        fn main() { unimpl/*caret*/ }
    """, """
        fn main() { unimplemented!(/*caret*/) }
    """)

    fun `test macro with square brackets`() = doFirstCompletion("""
        fn main() { vec/*caret*/ }
    """, """
        fn main() { vec![/*caret*/] }
    """)

    fun `test macro with braces`() = doFirstCompletion("""
       thread_lo/*caret*/
    """, """
       thread_local! {/*caret*/}
    """)

    fun `test macro in use item`() = doSingleCompletion("""
       #![feature(use_extern_macros)]

       pub use std::unimpl/*caret*/
    """, """
       #![feature(use_extern_macros)]

       pub use std::unimplemented;/*caret*/
    """)

    fun `test rustc doc only macro from prelude`() = doSingleCompletion("""
        fn main() { stringif/*caret*/ }
    """, """
        fn main() { stringify!(/*caret*/) }
    """)

    fun `test rustc doc only macro from std`() = doSingleCompletion("""
        fn main() { std::stringif/*caret*/ }
    """, """
        fn main() { std::stringify!(/*caret*/) }
    """)

    fun `test complete all in std in 'use' in crate root`() = checkContainsCompletion("vec", """
        use std::/*caret*/;
    """)

    @ProjectDescriptor(WithActualStdlibRustProjectDescriptor::class)
    fun `test complete items from 'os' module unix`() {
        if (!SystemInfo.isUnix && project.toolchain !is RsWslToolchain) return
        doSingleCompletion("""
            use std::os::uni/*caret*/
        """, """
            use std::os::unix/*caret*/
        """)
    }

    @ProjectDescriptor(WithActualStdlibRustProjectDescriptor::class)
    fun `test complete items from 'os' module windows`() {
        if (!SystemInfo.isWindows || project.toolchain is RsWslToolchain) return
        doSingleCompletion("""
            use std::os::win/*caret*/
        """, """
            use std::os::windows/*caret*/
        """)
    }

    fun `test don't complete borrow method`() = checkNotContainsCompletion("borrow", """
        fn main() {
            0.borro/*caret*/
        }
    """)

    fun `test complete iter enumerate (vector)`() = doSingleCompletion("""
        fn main() {
            let v = vec![1, 2, 3];
            v.enumerat/*caret*/
        }
    """, """
        fn main() {
            let v = vec![1, 2, 3];
            v.iter().enumerate()/*caret*/
        }
    """)

    fun `test complete iter enumerate (custom struct)`() = doSingleCompletion("""
        struct Foo {}
        impl Foo {
            fn iter(&self) -> impl Iterator<Item=i32> {
                vec![].into_iter()
            }
        }
        fn main() {
            let foo = Foo {};
            foo.enumerat/*caret*/
        }
    """, """
        struct Foo {}
        impl Foo {
            fn iter(&self) -> impl Iterator<Item=i32> {
                vec![].into_iter()
            }
        }
        fn main() {
            let foo = Foo {};
            foo.iter().enumerate()/*caret*/
        }
    """)

    fun `test complete chain methods if iter method is from imported trait`() = checkContainsCompletion("iter().enumerate", """
        struct Foo {}
        mod inner {
            pub trait Trait {
                fn iter(&self) -> std::vec::IntoIter<i32> {
                    vec![].into_iter()
                }
            }
            impl Trait for super::Foo {}
        }
        use inner::Trait;
        fn main() {
            let foo = Foo {};
            foo.enumerat/*caret*/
        }
    """)

    fun `test don't complete chain methods if iter method need import`() = checkNoCompletion("""
        struct Foo {}
        mod inner {
            pub trait Trait {
                fn iter(&self) -> std::vec::IntoIter<i32> {
                    vec![].into_iter()
                }
            }
            impl Trait for super::Foo {}
        }
        fn main() {
            let foo = Foo {};
            foo.enumerat/*caret*/
        }
    """)

    fun `test don't complete chain methods if name is not iter`() = checkNoCompletion("""
        struct Foo {}
        impl Foo {
            fn iter2(&self) -> impl Iterator<Item=i32> {
                vec![].into_iter()
            }
        }
        fn main() {
            let foo = Foo {};
            foo.enumerat/*caret*/
        }
    """)

    fun `test don't complete chain methods if type doesn't implement Iterator`() = checkNoCompletion("""
        struct Bar();
        impl Bar {
            fn enumerate(&self) {}
        }

        struct Foo {}
        impl Foo {
            fn iter(&self) -> Bar { Bar() }
        }
        fn main() {
            let foo = Foo {};
            foo.enumerat/*caret*/
        }
    """)
}
