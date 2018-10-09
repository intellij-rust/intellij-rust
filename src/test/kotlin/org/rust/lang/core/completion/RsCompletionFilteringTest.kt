/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsCompletionFilteringTest: RsCompletionTestBase() {
    fun `test unsatisfied bound filtered 1`() = doSingleCompletion("""
        trait Bound {}
        trait Trait1 { fn foo(&self) {} }
        trait Trait2 { fn bar(&self) {} }
        impl<T: Bound> Trait1 for T {}
        impl<T> Trait2 for T {}
        struct S;
        fn main() { S./*caret*/ }
    """, """
        trait Bound {}
        trait Trait1 { fn foo(&self) {} }
        trait Trait2 { fn bar(&self) {} }
        impl<T: Bound> Trait1 for T {}
        impl<T> Trait2 for T {}
        struct S;
        fn main() { S.bar()/*caret*/ }
    """)

    fun `test unsatisfied bound filtered 2`() = doSingleCompletion("""
        trait Bound1 {}
        trait Bound2 {}
        trait Trait1 { fn foo(&self) {} }
        trait Trait2 { fn bar(&self) {} }
        impl<T: Bound1> Trait1 for T {}
        impl<T: Bound2> Trait2 for T {}
        struct S;
        impl Bound1 for S {}
        fn main() { S./*caret*/ }
    """, """
        trait Bound1 {}
        trait Bound2 {}
        trait Trait1 { fn foo(&self) {} }
        trait Trait2 { fn bar(&self) {} }
        impl<T: Bound1> Trait1 for T {}
        impl<T: Bound2> Trait2 for T {}
        struct S;
        impl Bound1 for S {}
        fn main() { S.foo()/*caret*/ }
    """)

    fun `test unsatisfied bound not filtered for unknown type`() = doSingleCompletion("""
        trait Bound {}
        trait Trait { fn foo(&self) {} }
        impl<T: Bound> Trait for S1<T> {}
        struct S1<T>(T);
        fn main() { S1(SomeUnknownType).f/*caret*/ }
    """, """
        trait Bound {}
        trait Trait { fn foo(&self) {} }
        impl<T: Bound> Trait for S1<T> {}
        struct S1<T>(T);
        fn main() { S1(SomeUnknownType).foo()/*caret*/ }
    """)

    fun `test unsatisfied bound not filtered for unconstrained type var`() = doSingleCompletion("""
        trait Bound {}
        trait Trait { fn foo(&self) {} }
        impl<T: Bound> Trait for S1<T> {}
        struct S1<T>(T);
        fn ty_var<T>() -> T { unimplemented!() }
        fn main() { S1(ty_var()).f/*caret*/ }
    """, """
        trait Bound {}
        trait Trait { fn foo(&self) {} }
        impl<T: Bound> Trait for S1<T> {}
        struct S1<T>(T);
        fn ty_var<T>() -> T { unimplemented!() }
        fn main() { S1(ty_var()).foo()/*caret*/ }
    """)

    fun `test unsatisfied bound path filtering`() = doSingleCompletion("""
        trait Bound {}
        trait Trait1 { fn foo(){} }
        trait Trait2 { fn bar() {} }
        impl<T: Bound> Trait1 for T {}
        impl<T> Trait2 for T {}
        struct S;
        fn main() { S::/*caret*/ }
    """, """
        trait Bound {}
        trait Trait1 { fn foo(){} }
        trait Trait2 { fn bar() {} }
        impl<T: Bound> Trait1 for T {}
        impl<T> Trait2 for T {}
        struct S;
        fn main() { S::bar()/*caret*/ }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test method is not filtered by Sync+Send bounds`() = doSingleCompletion("""
        struct S;
        trait Trait { fn foo(&self) {} }
        impl<T: Sync + Send> Trait for T {}
        fn main() { S.fo/*caret*/ }
    """, """
        struct S;
        trait Trait { fn foo(&self) {} }
        impl<T: Sync + Send> Trait for T {}
        fn main() { S.foo()/*caret*/ }
    """)

    fun `test private function`() = checkNoCompletion("""
        mod foo { fn bar() {} }
        fn main() {
            foo::ba/*caret*/
        }
    """)

    fun `test private mod`() = checkNoCompletion("""
        mod foo { mod bar {} }
        fn main() {
            foo::ba/*caret*/
        }
    """)

    fun `test private enum`() = checkNoCompletion("""
        mod foo { enum MyEnum {} }
        fn main() {
            foo::MyEn/*caret*/
        }
    """)

    fun `test private method 1`() = checkNoCompletion("""
        mod foo {
            pub struct S;
            impl S { fn bar(&self) {} }
        }
        fn main() {
            foo::S.b/*caret*/()
        }
    """)

    fun `test private method 2`() = checkNoCompletion("""
        mod foo {
            pub struct S;
            impl S { fn bar(&self) {} }
        }
        fn main() {
            foo::S.b/*caret*/
        }
    """)

    fun `test private field`() = checkNoCompletion("""
        mod foo {
            pub struct S {
                field: i32
            }
        }
        fn bar(s: S) {
            s.f/*caret*/
        }
    """)

    fun `test doc(hidden) item`() = checkNoCompletion("""
        mod foo {
            #[doc(hidden)]
            pub struct MyStruct;
        }
        fn main() {
            foo::My/*caret*/
        }
    """)

    fun `test doc(hidden) item from the same module isn't filtered`() = doSingleCompletion("""
        #[doc(hidden)]
        struct MyStruct;
        fn main() {
            My/*caret*/
        }
    """, """
        #[doc(hidden)]
        struct MyStruct;
        fn main() {
            MyStruct/*caret*/
        }
    """)
}
