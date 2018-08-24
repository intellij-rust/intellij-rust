/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import junit.framework.AssertionFailedError
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsExternalDocUrlStdTest : RsDocumentationProviderTest() {
    fun `test mod`() = doUrlTestByText("""
        fn foo<T: std::fmt::Debug>(x: T) {}
                     //^
    """, "https://doc.rust-lang.org/alloc/fmt/index.html")

    fun `test function`() = doUrlTestByText("""
        fn main() {
            let v = std::env::var("HOME");
                             //^
        }
    """, "https://doc.rust-lang.org/std/env/fn.var.html")

    fun `test struct`() = doUrlTestByText("""
        fn foo(x: String) {}
                 //^
    """, "https://doc.rust-lang.org/alloc/string/struct.String.html")

    fun `test enum`() = doUrlTestByText("""
        fn foo(x: Option<i32>) {}
                  //^
    """, "https://doc.rust-lang.org/core/option/enum.Option.html")

    fun `test trait`() = doUrlTestByText("""
        fn foo<T: Eq>(x: T) {}
                //^
    """, "https://doc.rust-lang.org/core/cmp/trait.Eq.html")

    fun `test type`() = doUrlTestByText("""
        fn foo(x: std::fmt::Result) {}
                           //^
    """, "https://doc.rust-lang.org/core/fmt/type.Result.html")

    fun `test const`() = doUrlTestByText("""
        fn main() {
            let e = std::f64::consts::E;
                                    //^
        }
    """, "https://doc.rust-lang.org/core/f64/consts/constant.E.html")

    fun `test macro`() = expect<AssertionFailedError> {
        doUrlTestByText("""
            fn main() {
                println!("Hello!");
                //^
            }
        """, "https://doc.rust-lang.org/std/macro.println.html")
    }

    fun `test method`() = doUrlTestByText("""
        fn main() {
            let x = Vec::new();
                       //^
        }
    """, "https://doc.rust-lang.org/alloc/vec/struct.Vec.html#method.new")

    // FIXME: it should generate `https://doc.rust-lang.org/std/primitive.pointer.html#method.is_null`
    fun `test primitive method`() = doUrlTestByText("""
        fn foo(ptr: *const i32) {
            let x = ptr.is_null();
                          //^
        }
    """, "https://doc.rust-lang.org/core/primitive.pointer.html#method.is_null")

    fun `test tymethod`() = doUrlTestByText("""
        fn foo<T: Default>() -> T {
            T::default()
                //^
        }
    """, "https://doc.rust-lang.org/core/default/trait.Default.html#tymethod.default")

    fun `test associated type`() = expect<AssertionFailedError> {
        doUrlTestByText("""
        fn foo<T: Iterator>(x: T) -> T::Item {
                                       //^
            unimplemented!();
        }
    """, "https://doc.rust-lang.org/core/iter/trait.Iterator.html#associatedtype.Item")
    }

    fun `test enum variant`() = doUrlTestByText("""
        fn main() {
            let x = Some(123);
                   //^
        }
    """, "https://doc.rust-lang.org/core/option/enum.Option.html#variant.Some")

    fun `test struct field`() = doUrlTestByText("""
        fn foo(x: std::raw::TraitObject) {
            let data = x.data;
                         //^
        }
    """, "https://doc.rust-lang.org/core/raw/struct.TraitObject.html#structfield.data")
}
