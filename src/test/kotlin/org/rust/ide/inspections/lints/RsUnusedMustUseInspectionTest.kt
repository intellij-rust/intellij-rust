/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ProjectDescriptor
import org.rust.SkipTestWrapping
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase

class RsUnusedMustUseInspectionTest : RsInspectionsTestBase(RsUnusedMustUseInspection::class) {
    fun `test unused must_use with simple function call`() = checkWarnings("""
        #[must_use]
        fn foo() -> bool { false }

        fn main() {
            <weak_warning descr="Unused return value of foo that must be used">foo()</weak_warning>;
        }
    """)

    fun `test unused must_use with simple function call and inner attribute`() = checkWarnings("""
        fn foo() -> bool {
            #![must_use]
            false
        }

        fn main() {
            <weak_warning descr="Unused return value of foo that must be used">foo()</weak_warning>;
        }
    """)

    fun `test unused must_use with custom struct`() = checkWarnings("""
        #[must_use]
        struct S;

        fn main() {
            <weak_warning descr="Unused S that must be used">S</weak_warning>;
        }
    """)

    fun `test unused must_use with method call though nested struct literal`() = checkWarnings("""
        struct S;

        impl S {
            #[must_use]
            fn foo(&self) -> S { S }
        }

        fn main() {
            <weak_warning descr="Unused return value of foo that must be used">S.foo()</weak_warning>;
        }
    """)

    fun `test unused must_use with marked struct returned from function`() = checkWarnings("""
        #[must_use]
        struct S;

        fn foo() -> S { S }

        fn main() {
            <weak_warning descr="Unused S that must be used">foo()</weak_warning>;
        }
    """)

    fun `test unused must_use with marked trait returned from function`() = checkWarnings("""
        struct Foo();

        #[must_use]
        pub trait Trait {}
        impl Trait for Foo {}

        fn bar() -> impl Trait {
            Foo()
        }
        fn main() {
            /*weak_warning descr="Unused impl Trait that must be used"*/bar()/*weak_warning**/;
        }
    """)

    fun `test unused must_use with method call`() = checkWarnings("""
        struct S;

        impl S {
            #[must_use]
            fn foo(&self) -> S { S }
        }

        struct S2 { s: S }

        fn main() {
            <weak_warning descr="Unused return value of foo that must be used">S2 { s: S }.s.foo()</weak_warning>;
        }
    """)

    fun `test unused must_use block disabled by cfg`() = checkWarnings("""
        #[must_use]
        struct S;

        fn xyz() -> S {
            #[cfg(undeclared_feature)]
            { S }

            #[cfg(not(undeclared_feature))]
            <weak_warning descr="Unused S that must be used">{ S }</weak_warning>

            { S }
        }
    """)

    @SkipTestWrapping  // TODO Fix
    fun `test no warning on reverse cfg disabled blocks`() = checkWarnings("""
        #[must_use]
        struct S;

        fn xyz() -> S {
            #[cfg(not(undeclared_feature))]
            { S }

            #[cfg(undeclared_feature)]
            { S }
        }
    """)

    fun `test fixing by adding assigning to _`() = checkFixByText("Add `let _ =`","""
        #[must_use]
        fn foo() -> bool { false }

        fn main() {
            <weak_warning descr="Unused return value of foo that must be used">/*caret*/foo()</weak_warning>;
        }
    """, """
        #[must_use]
        fn foo() -> bool { false }

        fn main() {
            let _ = foo();
        }
    """, checkWeakWarn = true)

    @SkipTestWrapping // TODO test live templates
    fun `test fixing by adding assigning to _ with template`() = checkFixByTextWithLiveTemplate("Add `let _ =`","""
        #[must_use]
        fn foo() -> bool { false }

        fn main() {
            <weak_warning descr="Unused return value of foo that must be used">/*caret*/foo()</weak_warning>;
        }
    """, "a\t", """
        #[must_use]
        fn foo() -> bool { false }

        fn main() {
            let a = foo();
        }
    """, checkWeakWarn = true)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fixing unused result by adding unwrap`() = checkFixByText("Add `.unwrap()`","""
        fn foo() -> Result<bool, ()> { false }

        fn main() {
            <weak_warning descr="Unused Result<bool, ()> that must be used">/*caret*/foo()</weak_warning>;
        }
    """, """
        fn foo() -> Result<bool, ()> { false }

        fn main() {
            foo().unwrap();
        }
    """, checkWeakWarn = true)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fixing unused result by adding expect`() = checkFixByText("Add `.expect(\"\")`","""
        fn foo() -> Result<bool, ()> { false }

        fn main() {
            <weak_warning descr="Unused Result<bool, ()> that must be used">/*caret*/foo()</weak_warning>;
        }
    """, """
        fn foo() -> Result<bool, ()> { false }

        fn main() {
            foo().expect("TODO: panic message");
        }
    """, checkWeakWarn = true)

    @SkipTestWrapping // TODO test live templates
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fixing unused result by adding expect with template`() = checkFixByTextWithLiveTemplate("Add `.expect(\"\")`","""
        fn foo() -> Result<bool, ()> { false }

        fn main() {
            <weak_warning descr="Unused Result<bool, ()> that must be used">/*caret*/foo()</weak_warning>;
        }
    """, "abc\t", """
        fn foo() -> Result<bool, ()> { false }

        fn main() {
            foo().expect("abc");
        }
    """, checkWeakWarn = true)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fixing adding await for impl Future`() = checkFixByText("Add `.await`", """
        fn foo() -> impl std::future::Future {
            async {}
        }
        async fn test() {
            /*weak_warning descr="Unused impl Future that must be used"*//*caret*/foo()/*weak_warning**/;
        }
    """, """
        fn foo() -> impl std::future::Future {
            async {}
        }
        async fn test() {
            foo().await;
        }
    """, checkWeakWarn = true)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fixing adding await for Box dyn Future`() = checkFixByText("Add `.await`", """
        fn foo() -> Box<dyn std::future::Future<Output=()> + Unpin> {
            todo!()
        }
        async fn test() {
            /*weak_warning descr="Unused dyn Future that must be used"*//*caret*/foo()/*weak_warning**/;
        }
    """, """
        fn foo() -> Box<dyn std::future::Future<Output=()> + Unpin> {
            todo!()
        }
        async fn test() {
            foo().await;
        }
    """, checkWeakWarn = true)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fixing adding await for struct implementing Future`() = checkFixByText("Add `.await`", """
        use std::future::Future;
        use std::pin::Pin;
        use std::task::{Context, Poll};

        #[must_use]
        struct Foo();
        impl Future for Foo {
            type Output = ();
            fn poll(self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Self::Output> { todo!() }
        }

        fn foo() -> Foo { Foo() }
        async fn test() {
            /*weak_warning descr="Unused Foo that must be used"*//*caret*/foo()/*weak_warning**/;
        }
    """, """
        use std::future::Future;
        use std::pin::Pin;
        use std::task::{Context, Poll};

        #[must_use]
        struct Foo();
        impl Future for Foo {
            type Output = ();
            fn poll(self: Pin<&mut Self>, _: &mut Context<'_>) -> Poll<Self::Output> { todo!() }
        }

        fn foo() -> Foo { Foo() }
        async fn test() {
            foo().await;
        }
    """, checkWeakWarn = true)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no fix add await for not impl Future`() = checkFixIsUnavailable("Add `.await`", """
        struct Foo();

        #[must_use]
        pub trait Trait {}
        impl Trait for Foo {}

        fn foo() -> impl Trait { Foo() }

        async fn test() {
            /*weak_warning descr="Unused impl Trait that must be used"*//*caret*/foo()/*weak_warning**/;
        }
    """, checkWeakWarn = true)
}
