/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.fileTree

class RsCompletionTest : RsCompletionTestBase() {
    fun `test local variable`() = doSingleCompletion("""
        fn foo(quux: i32) { qu/*caret*/ }
    """, """
        fn foo(quux: i32) { quux/*caret*/ }
    """)

    fun `test function call zero args`() = doSingleCompletion("""
        fn frobnicate() {}
        fn main() { frob/*caret*/ }
    """, """
        fn frobnicate() {}
        fn main() { frobnicate()/*caret*/ }
    """)

    fun `test function call one arg`() = doSingleCompletion("""
        fn frobnicate(foo: i32) {}
        fn main() { frob/*caret*/ }
    """, """
        fn frobnicate(foo: i32) {}
        fn main() { frobnicate(/*caret*/) }
    """)

    fun `test function call with parens`() = doSingleCompletion("""
        fn frobnicate() {}
        fn main() { frob/*caret*/() }
    """, """
        fn frobnicate() {}
        fn main() { frobnicate()/*caret*/ }
    """)

    fun `test function call with parens with arg`() = doSingleCompletion("""
        fn frobnicate(foo: i32) {}
        fn main() { frob/*caret*/() }
    """, """
        fn frobnicate(foo: i32) {}
        fn main() { frobnicate(/*caret*/) }
    """)

    fun `test path`() = doSingleCompletion("""
        mod foo {
            mod bar { fn frobnicate() {} }
        }
        fn frobfrobfrob() {}

        fn main() {
            foo::bar::frob/*caret*/
        }
    """, """
        mod foo {
            mod bar { fn frobnicate() {} }
        }
        fn frobfrobfrob() {}

        fn main() {
            foo::bar::frobnicate()/*caret*/
        }
    """)

    fun `test anonymous item does not break completion`() = doSingleCompletion("""
        extern "C" { }
        fn frobnicate() {}

        fn main() {
            frob/*caret*/
        }
    """, """
        extern "C" { }
        fn frobnicate() {}

        fn main() {
            frobnicate()/*caret*/
        }
    """)

    fun `test use glob`() = doSingleCompletion("""
        mod foo { pub fn quux() {} }

        use self::foo::q/*caret*/;
    """, """
        mod foo { pub fn quux() {} }

        use self::foo::quux/*caret*/;
    """)

    fun `test use glob global`() = doSingleCompletion("""
        pub struct Foo;

        mod m {
            use F/*caret*/;
        }
    """, """
        pub struct Foo;

        mod m {
            use Foo/*caret*/;
        }
    """)

    fun `test use item`() = doSingleCompletion("""
        mod foo { pub fn quux() {} }
        use self::foo::q/*caret*/;
    """, """
        mod foo { pub fn quux() {} }
        use self::foo::quux/*caret*/;
    """)

    fun `test wildcard imports`() = doSingleCompletion("""
        mod foo { fn transmogrify() {} }

        fn main() {
            use self::foo::*;
            trans/*caret*/
        }
    """, """
        mod foo { fn transmogrify() {} }

        fn main() {
            use self::foo::*;
            transmogrify()/*caret*/
        }
    """)

    fun `test shadowing`() = doSingleCompletion("""
        fn main() {
            let foobar = "foobar";
            let foobar = foobar.to_string();
            foo/*caret*/
        }
    """, """
        fn main() {
            let foobar = "foobar";
            let foobar = foobar.to_string();
            foobar/*caret*/
        }
    """)

    fun `test complete alias`() = doSingleCompletion("""
        mod m { pub fn transmogrify() {} }
        use self::m::transmogrify as frobnicate;

        fn main() {
            frob/*caret*/
        }
    """, """
        mod m { pub fn transmogrify() {} }
        use self::m::transmogrify as frobnicate;

        fn main() {
            frobnicate()/*caret*/
        }
    """)

    fun `test complete self type`() = doSingleCompletion("""
        trait T { fn foo() -> Se/*caret*/ }
    """, """
        trait T { fn foo() -> Self/*caret*/ }
    """)

    fun `test struct field`() = doSingleCompletion("""
        struct S { foobarbaz: i32 }
        fn main() {
            let _ = S { foo/*caret*/ };
        }
    """, """
        struct S { foobarbaz: i32 }
        fn main() {
            let _ = S { foobarbaz/*caret*/ };
        }
    """)

    fun `test enum field`() = doSingleCompletion("""
        enum E { X { bazbarfoo: i32 } }
        fn main() {
            let _ = E::X { baz/*caret*/ }
        }
    """, """
        enum E { X { bazbarfoo: i32 } }
        fn main() {
            let _ = E::X { bazbarfoo/*caret*/ }
        }
    """)

    fun `test local scope`() = checkNoCompletion("""
        fn foo() {
            let x = spam/*caret*/;
            let spamlot = 92;
        }
    """)

    fun `test while let`() = checkNoCompletion("""
        fn main() {
            while let Some(quazimagnitron) = quaz/*caret*/ { }
        }
    """)

    fun `test alias shadows original name`() = checkNoCompletion("""
        mod m {
            pub fn transmogrify() {}
        }

        use self::m::transmogrify as frobnicate;

        fn main() {
            trans/*caret*/
        }
    """)

    fun `test completion respects namespaces`() = checkNoCompletion("""
        fn foobar() {}

        fn main() {
            let _: foo/*caret*/ = unimplemented!();
        }
    """)

    fun `test child file`() = doSingleCompletion(fileTree {
        rust("main.rs", """
            use foo::Spam;
            mod foo;

            fn main() { let _ = Spam::Q/*caret*/; }
        """)
        rust("foo.rs", """
            pub enum Spam { Quux, Eggs }
        """)
    }, """
        use foo::Spam;
        mod foo;

        fn main() { let _ = Spam::Quux/*caret*/; }
    """)

    fun `test parent file`() = doSingleCompletion(fileTree {
        rust("main.rs", """
            mod foo;

            pub enum Spam { Quux, Eggs }

            fn main() { }
        """)
        rust("foo.rs", """
            use super::Spam;

            fn foo() { let _ = Spam::Q/*caret*/; }
        """)
    }, """
        use super::Spam;

        fn foo() { let _ = Spam::Quux/*caret*/; }
    """)

    fun `test parent file 2`() = doSingleCompletion(fileTree {
        rust("main.rs", """
            mod foo;

            pub enum Spam { Quux, Eggs }

            fn main() { }
        """)
        dir("foo") {
            rust("mod.rs", "use Spam::Qu/*caret*/;")
        }
    }, "use Spam::Quux/*caret*/;")

    fun testCallStructMethod() = checkContainsCompletion("some_fn", """
        struct SomeStruct { }
        impl SomeStruct {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeStruct { };
            v./*caret*/
        }
    """)

    fun testCallEnumMethod() = checkContainsCompletion("some_fn", """
        enum SomeEnum { Var1, Var2 }
        impl SomeEnum {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeEnum::Var1 ;
            v./*caret*/
        }
    """)

    fun testCallTraitImplForStructMethod() = checkContainsCompletion("some_fn", """
        trait SomeTrait { fn some_fn(&self); }
        struct SomeStruct { }
        impl SomeTrait for SomeStruct {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeStruct { };
            v./*caret*/
        }
    """)

    fun testCallTraitImplForEnumMethod() = checkContainsCompletion("some_fn", """
        trait SomeTrait { fn some_fn(&self); }
        enum SomeEnum { Var1, Var2 }
        impl SomeTrait for SomeEnum {
            fn some_fn(&self) {}
        }
        fn main() {
            let v = SomeEnum::Var1 ;
            v./*caret*/
        }
    """)

    fun testEnumVariant() = checkSingleCompletion("BAZBAR", """
        enum Foo {
            BARBOO,
            BAZBAR
        }
        fn main() {
            let _ = Foo::BAZ/*caret*/
        }
    """)

    fun testEnumVariantWithTupleFields() = checkSingleCompletion("Foo::BARBAZ()", """
        enum Foo {
            BARBAZ(f64)
        }
        fn main() {
            let _ = Foo::BAR/*caret*/
        }
    """)

    fun testEnumVariantWithTupleFieldsInUseBlock() = checkSingleCompletion("BARBAZ", """
        enum Foo {
            BARBAZ(f64)
        }
        fn main() {
            use Foo::BAR/*caret*/
        }
    """)

    fun testEnumVariantWithBlockFields() = checkSingleCompletion("Foo::BARBAZ {}", """
        enum Foo {
            BARBAZ {
                foo: f64
            }
        }
        fn main() {
            let _ = Foo::BAR/*caret*/
        }
    """)

    fun testEnumVariantWithBlockFieldsInUseBlock() = checkSingleCompletion("BARBAZ", """
        enum Foo {
            BARBAZ {
                foo: f64
            }
        }
        fn main() {
            use Foo::BAR/*caret*/
        }
    """)

    fun testTypeNamespaceIsCompletedForPathHead() = checkSingleCompletion("FooBar", """
        struct FooBar { f: i32 }

        fn main() {
            Foo/*caret*/
        }
    """)

    // issue #1182
    fun testAssociatedTypeCompletion() = checkSingleCompletion("Bar", """
        trait Foo {
            type Bar;
            fn foo(bar: Self::Ba/*caret*/);
        }
    """)

    // issue #1182
    fun testAssociatedTypeSuggestion() = checkContainsCompletion("Bar", """
        trait Foo {
            type Bar;
            fn foo(bar: Self::/*caret*/);
        }
    """)

    // issue #1182
    fun testAssociatedTypeSuggestionWithReference() = checkContainsCompletion("Bar", """
        trait Foo {
            type Bar;
            fn foo(bar: &mut Self::/*caret*/);
        }
    """)

    fun `test complete enum variants 1`() = checkSingleCompletion("BinOp", """
        enum Expr { Unit, BinOp(Box<Expr>, Box<Expr>) }
        fn foo(e: Expr) {
            use self::Expr::*;
            match e {
                Bi/*caret*/
            }
        }
    """)

    fun `test complete enum variants 2`() = checkSingleCompletion("Unit", """
        enum Expr { Unit, BinOp(Box<Expr>, Box<Expr>) }
        fn foo(e: Expr) {
            use self::Expr::*;
            match e {
                Un/*caret*/
            }
        }
    """)

    fun `test should not complete for test functions`() = checkNoCompletion("""
        #[test]
        fn foobar() {}

        fn main() {
            foo/*caret*/
        }
    """)

    fun `test complete macro`() = checkSingleCompletion("foo_bar", """
        macro_rules! foo_bar { () => () }
        fn main() {
            fo/*caret*/
        }
    """)

    fun `test complete outer macro`() = checkSingleCompletion("foo_bar", """
        macro_rules! foo_bar { () => () }
        fo/*caret*/
        fn main() {
        }
    """)

    fun `test macro don't suggests as function name`() = checkNoCompletion("""
        macro_rules! foo_bar { () => () }
        fn foo/*caret*/() {
        }
    """)

}
