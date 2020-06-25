/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.openapi.project.DumbServiceImpl
import org.intellij.lang.annotations.Language
import org.rust.lang.core.completion.RsKeywordCompletionContributor.Companion.CONDITION_KEYWORDS

class RsKeywordCompletionContributorTest : RsCompletionTestBase() {

    override fun setUp() {
        super.setUp()
        DumbServiceImpl.getInstance(project).isDumb = true
    }

    override fun tearDown() {
        DumbServiceImpl.getInstance(project).isDumb = false
        super.tearDown()
    }

    fun `test break in for loop`() = checkCompletion("break", """
        fn foo() {
            for _ in 0..4 {
                bre/*caret*/
            }
        }
    """, """
        fn foo() {
            for _ in 0..4 {
                break/*caret*/
            }
        }
    """)

    fun `test break in loop`() = checkCompletion("break", """
        fn foo() {
            loop {
                br/*caret*/
            }
        }
    """, """
        fn foo() {
            loop {
                break/*caret*/
            }
        }
    """)

    fun `test break in while loop`() = checkCompletion("break", """
        fn foo() {
            while true {
                brea/*caret*/
            }
        }
    """, """
        fn foo() {
            while true {
                break/*caret*/
            }
        }
    """)

    fun `test break not applied if doesnt start stmt`() = checkNoCompletion("""
        fn foo() {
            while true {
                let brea/*caret*/
            }
        }
    """)

    fun `test break not applied outside loop`() = checkNoCompletion("""
        fn foo() {
            bre/*caret*/
        }
    """)

    fun `test break not applied within closure`() = checkNoCompletion("""
        fn bar() {
            loop {
                let _ = || { bre/*caret*/ }
            }
        }
    """)

    fun `test continue in for loop`() = checkCompletion("continue", """
        fn foo() {
            for _ in 0..4 {
                cont/*caret*/
            }
        }
    """, """
        fn foo() {
            for _ in 0..4 {
                continue/*caret*/
            }
        }
    """)

    fun `test continue in loop`() = checkCompletion("continue", """
        fn foo() {
            loop {
                cont/*caret*/
            }
        }
    """, """
        fn foo() {
            loop {
                continue/*caret*/
            }
        }
    """)

    fun `test continue in while loop`() = checkCompletion("continue", """
        fn foo() {
            while true {
                conti/*caret*/
            }
        }
    """, """
        fn foo() {
            while true {
                continue/*caret*/
            }
        }
    """)

    fun `test const`() = checkCompletion("const", """
        con/*caret*/
    """, """
        const /*caret*/
    """)

    fun `test pub const`() = checkCompletion("const", """
        pub con/*caret*/
    """, """
        pub const /*caret*/
    """)

    fun `test enum`() = checkCompletion("enum", """
        enu/*caret*/
    """, """
        enum /*caret*/
    """)

    fun `test enum at the file very beginning`() = checkCompletion("enum",
        "enu/*caret*/",
        "enum /*caret*/"
    )

    fun `test pub enum`() = checkCompletion("enum", """
        pub enu/*caret*/
    """, """
        pub enum /*caret*/
    """)

    fun `test enum within mod`() = checkCompletion("enum", """
        mod foo {
            en/*caret*/
        }
    """, """
        mod foo {
            enum /*caret*/
        }
    """)

    fun `test enum within fn`() = checkCompletion("enum", """
        fn foo() {
            en/*caret*/
        }
    """, """
        fn foo() {
            enum /*caret*/
        }
    """)

    fun `test enum within fn nested block`() = checkCompletion("enum", """
        fn foo() {{
            en/*caret*/
        }}
    """, """
        fn foo() {{
            enum /*caret*/
        }}
    """)

    fun `test enum within fn after other stmt`() = checkCompletion("enum", """
        fn foo() {
            let _ = 10;
            en/*caret*/
        }
    """, """
        fn foo() {
            let _ = 10;
            enum /*caret*/
        }
    """)

    fun `test enum not applied if doesnt start stmt within fn`() = checkNoCompletion("""
        fn foo() {
            let en/*caret*/
        }
    """)

    fun `test enum not applied within struct`() = checkNoCompletion("""
        struct Foo {
            en/*caret*/
        }
    """)

    fun `test enum not applied if doesnt start stmt`() = checkNoCompletion("""
        mod en/*caret*/
    """)

    fun `test extern`() = checkCompletion("extern", """
        ext/*caret*/
    """, """
        extern /*caret*/
    """)

    fun `test pub extern`() = checkCompletion("extern", """
        pub ext/*caret*/
    """, """
        pub extern /*caret*/
    """)

    fun `test unsafe extern`() = checkCompletion("extern", """
        unsafe ext/*caret*/
    """, """
        unsafe extern /*caret*/
    """)

    fun `test pub unsafe extern`() = checkCompletion("extern", """
        pub unsafe ext/*caret*/
    """, """
        pub unsafe extern /*caret*/
    """)

    fun `test extern crate`() = checkCompletion("crate", """
        extern cr/*caret*/
    """, """
        extern crate /*caret*/
    """)

    fun `test crate not applied at file beginning`() = checkNoCompletion("crat/*caret*/")

    fun `test crate not applied without prefix`() = checkNoCompletion("""
        crat/*caret*/
    """)

    fun `test fn`() = checkContainsCompletion("fn", """
        f/*caret*/
    """)

    fun `test pub fn`() = checkContainsCompletion("fn", """
        pub f/*caret*/
    """)

    fun `test extern fn`() = checkCompletion("fn", """
        extern f/*caret*/
    """, """
        extern fn /*caret*/
    """)

    fun `test unsafe fn`() = checkCompletion("fn", """
        unsafe f/*caret*/
    """, """
        unsafe fn /*caret*/
    """)

    fun `test impl`() = checkCompletion("impl", """
        imp/*caret*/
    """, """
        impl /*caret*/
    """)

    fun `test unsafe impl`() = checkCompletion("impl", """
        unsafe im/*caret*/
    """, """
        unsafe impl /*caret*/
    """)

    fun `test let within fn`() = checkCompletion("let", """
        fn main() {
            let a = 12;
            le/*caret*/
        }
    """, """
        fn main() {
            let a = 12;
            let /*caret*/
        }
    """)

    fun `test let within assoc fn`() = checkCompletion("let", """
        struct Foo;
        impl Foo {
            fn shutdown() { le/*caret*/ }
        }
    """, """
        struct Foo;
        impl Foo {
            fn shutdown() { let /*caret*/ }
        }
    """)

    fun `test let within method`() = checkCompletion("let", """
        struct Foo;
        impl Foo {
            fn calc(&self) { le/*caret*/ }
        }
    """, """
        struct Foo;
        impl Foo {
            fn calc(&self) { let /*caret*/ }
        }
    """)

    fun `test let not applied within nested mod`() = checkNoCompletion("""
        fn foo() {
            mod bar {
                le/*caret*/
            }
        }
    """)

    fun `test mod`() = checkCompletion("mod", """
        mo/*caret*/
    """, """
        mod /*caret*/
    """)

    fun `test pub mod`() = checkCompletion("mod", """
        pub mo/*caret*/
    """, """
        pub mod /*caret*/
    """)

    fun `test mut`() = checkCompletion("mut", """
        fn main() {
            let mu/*caret*/
        }
    """, """
        fn main() {
            let mut /*caret*/
        }
    """)

    fun `test return within fn`() = checkCompletion("return", """
        fn main() {
            re/*caret*/
        }
    """, """
        fn main() {
            return;/*caret*/
        }
    """)

    fun `test return within assoc fn`() = checkCompletion("return", """
        struct Foo;
        impl Foo {
            fn shutdown() { retu/*caret*/ }
        }
    """, """
        struct Foo;
        impl Foo {
            fn shutdown() { return;/*caret*/ }
        }
    """)

    fun `test return within method`() = checkCompletion("return", """
        struct Foo;
        impl Foo {
            fn print(&self) { retu/*caret*/ }
        }
    """, """
        struct Foo;
        impl Foo {
            fn print(&self) { return;/*caret*/ }
        }
    """)

    fun `test return not applied on file level`() = checkNoCompletion("""
        retu/*caret*/
    """)

    fun `test return not applied within parameters list`() = checkNoCompletion("""
        fn foo(retu/*caret*/) {}
    """)

    fun `test return not applied before block`() = checkNoCompletion("""
        fn foo() retu/*caret*/ {}
    """)

    fun `test return not applied if doesnt start statement`() = checkNoCompletion("""
        const retu/*caret*/
    """)

    fun `test static`() = checkCompletion("static", """
        sta/*caret*/
    """, """
        static /*caret*/
    """)

    fun `test pub static`() = checkCompletion("static", """
        pub stat/*caret*/
    """, """
        pub static /*caret*/
    """)

    fun `test struct`() = checkCompletion("struct", """
        str/*caret*/
    """, """
        struct /*caret*/
    """)

    fun `test pub struct`() = checkCompletion("struct", """
        pub str/*caret*/
    """, """
        pub struct /*caret*/
    """)

    fun `test trait`() = checkCompletion("trait", """
        tra/*caret*/
    """, """
        trait /*caret*/
    """)

    fun `test pub trait`() = checkCompletion("trait", """
        pub tra/*caret*/
    """, """
        pub trait /*caret*/
    """)

    fun `test unsafe trait`() = checkCompletion("trait", """
        unsafe tra/*caret*/
    """, """
        unsafe trait /*caret*/
    """)

    fun `test type`() = checkCompletion("type", """
        typ/*caret*/
    """, """
        type /*caret*/
    """)

    fun `test pub type`() = checkCompletion("type", """
        pub typ/*caret*/
    """, """
        pub type /*caret*/
    """)

    fun `test unsafe`() = checkCompletion("unsafe", """
        uns/*caret*/
    """, """
        unsafe /*caret*/
    """)

    fun `test pub unsafe`() = checkCompletion("unsafe", """
        pub unsa/*caret*/
    """, """
        pub unsafe /*caret*/
    """)

    fun `test use`() = checkCompletion("use", """
        us/*caret*/
    """, """
        use /*caret*/
    """)

    fun `test pub use`() = checkCompletion("use", """
        pub us/*caret*/
    """, """
        pub use /*caret*/
    """)

    fun `test else`() = checkCompletion("else", """
        fn main() {
            if true { } /*caret*/
        }
    """, """
        fn main() {
            if true { } else { /*caret*/ }
        }
    """)

    fun `test else if`() = checkCompletion("else if", """
        fn main() {
            if true { } /*caret*/
        }
    """, """
        fn main() {
            if true { } else if /*caret*/ { }
        }
    """)

    fun `test return from unit function`() = checkCompletion("return",
        "fn foo() { ret/*caret*/}",
        "fn foo() { return;/*caret*/}"
    )

    fun `test return from explicit unit function`() = checkCompletion("return",
        "fn foo() -> () { ret/*caret*/}",
        "fn foo() -> () { return;/*caret*/}"
    )

    fun `test return from non-unit function`() = checkCompletion("return",
        "fn foo() -> i32 { ret/*caret*/}",
        "fn foo() -> i32 { return /*caret*/}"
    )

    fun `test where in generic function`() = checkCompletion("where", """
        fn foo<T>(t: T) whe/*caret*/
    """, """
        fn foo<T>(t: T) where /*caret*/
    """)

    fun `test where in generic function with ret type`() = checkCompletion("where", """
        fn foo<T>(t: T) -> i32 whe/*caret*/
    """, """
        fn foo<T>(t: T) -> i32 where /*caret*/
    """)

    fun `test where in not generic function`() = checkNoCompletion("""
        fn foo() whe/*caret*/
    """)

    fun `test where in not generic function with ret type`() = checkNoCompletion("""
        fn foo() -> i32 whe/*caret*/
    """)

    fun `test where in trait method`() = checkCompletion("where", """
        trait Foo {
            fn foo() whe/*caret*/
        }
    """, """
        trait Foo {
            fn foo() where /*caret*/
        }
    """)

    fun `test where in method`() = checkCompletion("where", """
        impl Foo {
            fn foo() whe/*caret*/
        }
    """, """
        impl Foo {
            fn foo() where /*caret*/
        }
    """)

    fun `test where in generic struct`() = checkCompletion("where", """
        struct Foo<T> whe/*caret*/
    """, """
        struct Foo<T> where /*caret*/
    """)

    fun `test where in generic tuple struct`() = checkCompletion("where", """
        struct Foo<T>(T) whe/*caret*/
    """, """
        struct Foo<T>(T) where /*caret*/
    """)

    fun `test where in not generic struct`() = checkNoCompletion("""
        struct Foo whe/*caret*/
    """)

    fun `test where in generic enum`() = checkCompletion("where", """
        enum Foo<T> whe/*caret*/
    """, """
        enum Foo<T> where /*caret*/
    """)

    fun `test where in not generic enum`() = checkNoCompletion("""
        enum Foo whe/*caret*/
    """)

    fun `test where in generic type alias`() = checkCompletion("where", """
        type Foo<T> whe/*caret*/
    """, """
        type Foo<T> where /*caret*/
    """)

    fun `test where in not generic type alias`() = checkNoCompletion("""
        type Foo whe/*caret*/
    """)

    fun `test where in trait assoc type`() = checkNoCompletion("""
        trait Foo {
            type Bar whe/*caret*/
        }
    """)

    fun `test where in impl block assoc type`() = checkNoCompletion("""
        impl Foo for Bar {
            type FooBar whe/*caret*/
        }
    """)

    fun `test where in trait`() = checkCompletion("where", """
        trait Foo whe/*caret*/
    """, """
        trait Foo where /*caret*/
    """)

    fun `test where in generic trait`() = checkCompletion("where", """
        trait Foo<T> whe/*caret*/
    """, """
        trait Foo<T> where /*caret*/
    """)

    fun `test where in impl`() = checkCompletion("where", """
        impl Foo whe/*caret*/
    """, """
        impl Foo where /*caret*/
    """)

    fun `test where in trait impl`() = checkCompletion("where", """
        impl<T> Foo<T> for Bar whe/*caret*/
    """, """
        impl<T> Foo<T> for Bar where /*caret*/
    """)

    fun `test if|match in start of statement`() = checkCompletion(CONDITION_KEYWORDS, """
        fn foo() {
            /*caret*/
        }
    """, """
        fn foo() {
            /*lookup*/ /*caret*/ { }
        }
    """)

    fun `test if|match in let statement`() = checkCompletion(CONDITION_KEYWORDS, """
        fn foo() {
            let x = /*caret*/
        }
    """, """
        fn foo() {
            let x = /*lookup*/ /*caret*/ { };
        }
    """)

    fun `test if|match in let statement with semicolon`() = checkCompletion(CONDITION_KEYWORDS, """
        fn foo() {
            let x = /*caret*/;
        }
    """, """
        fn foo() {
            let x = /*lookup*/ /*caret*/ { };
        }
    """)

    fun `test if|match in expression`() = checkCompletion(CONDITION_KEYWORDS, """
        fn foo() {
            let x = 1 + /*caret*/
        }
    """, """
        fn foo() {
            let x = 1 + /*lookup*/ /*caret*/ { }
        }
    """)

    fun `test no if|match after path segment`() = checkCompletion(CONDITION_KEYWORDS, """
        struct Foo;

        fn foo() {
            Foo::/*caret*/
        }
    """, """
        struct Foo;

        fn foo() {
            Foo::/*caret*/
        }
    """)

    fun `test no if|match out of function`() = checkCompletion(CONDITION_KEYWORDS, """
        const FOO: &str = /*caret*/
    """, """
        const FOO: &str = /*caret*/
    """)

    fun `test complete full kw`() = doSingleCompletion("""
        fn main() {
            let/*caret*/
        }
    """, """
        fn main() {
            let /*caret*/
        }
    """)

    fun `test const parameter first`() = checkCompletion("const",
        "fn foo</*caret*/>() {}",
        "fn foo<const /*caret*/>() {}"
    )

    fun `test const parameter before lifetime parameter`() = checkNoCompletion("""
        "fn foo</*caret*/, 'a>() {}"
    """)

    fun `test const parameter before type parameter`() = checkNoCompletion("""
        "fn foo</*caret*/, A>() {}"
    """)

    fun `test const parameter before const parameter`() = checkCompletion("const",
        "fn foo</*caret*/, const C: i32>() {}",
        "fn foo<const /*caret*/, const C: i32>() {}"
    )

    fun `test const parameter after lifetime parameter`() = checkCompletion("const",
        "fn foo<'a, /*caret*/>() {}",
        "fn foo<'a, const /*caret*/>() {}"
    )

    fun `test const parameter after type parameter`() = checkCompletion("const",
        "fn foo<A, /*caret*/>() {}",
        "fn foo<A, const /*caret*/>() {}"
    )

    fun `test const parameter after const parameter`() = checkCompletion("const",
        "fn foo<const C: i32, /*caret*/>() {}",
        "fn foo<const C: i32, const /*caret*/>() {}"
    )

    fun `test const parameter before comma`() = checkNoCompletion("""
        "fn foo<T /*caret*/>() {}"
    """)

    fun `test inside trait`() = checkCompletion(MEMBERS_KEYWORDS, """
        pub trait Bar {
            /*caret*/
            const C: i32 = 1;
        }
    ""","""
        pub trait Bar {
            /*lookup*/ /*caret*/
            const C: i32 = 1;
        }
    """)

    fun `test inside trait after statement`() = checkCompletion(MEMBERS_KEYWORDS, """
        pub trait Bar {
            const C: i32 = 1;
            /*caret*/
        }
    ""","""
        pub trait Bar {
            const C: i32 = 1;
            /*lookup*/ /*caret*/
        }
    """)

    fun `test enum inside trait`() = checkNoCompletion("""
        pub trait Bar {
            en/*caret*/
        }
    """)

    fun `test trait inside trait`() = checkNoCompletion("""
        pub trait Bar {
            tra/*caret*/
        }
    """)

    fun `test inside trait impl`() = checkCompletion(MEMBERS_KEYWORDS, """
        impl Bar for Foo {
            /*caret*/
            const C: i32 = 1;
        }
    ""","""
        impl Bar for Foo {
            /*lookup*/ /*caret*/
            const C: i32 = 1;
        }
    """)

    fun `test inside impl after statement`() = checkCompletion(MEMBERS_KEYWORDS, """
        impl Bar for Foo {
            const C: i32 = 1;
            /*caret*/
        }
    ""","""
        impl Bar for Foo {
            const C: i32 = 1;
            /*lookup*/ /*caret*/
        }
    """)

    fun `test impl inside impl`() = checkNoCompletion("""
        impl Bar for Foo {
            imp/*caret*/
        }
    """)

    fun `test unsafe fn in impl`() = checkCompletion("fn", """
        impl Foo {
            unsafe f/*caret*/
        }
    """, """
        impl Foo {
            unsafe fn /*caret*/
        }
    """)

    fun `test pub member keyword in inherent impl`() = checkCompletion(MEMBERS_KEYWORDS, """
        impl Foo {
            pub /*caret*/
        }
    """, """
        impl Foo {
            pub /*lookup*/ /*caret*/
        }
    """)

    fun `test pub keyword in inherent impl`() = checkCompletion("pub", """
        impl Foo {
            pu/*caret*/
        }
    """, """
        impl Foo {
            pub /*caret*/
        }
    """)

    fun `test no pub keyword in trait`() = checkNoCompletion("""
        trait Foo {
            pu/*caret*/
        }
    """)

    fun `test no pub keyword in trait impl`() = checkNoCompletion("""
        impl Foo for Bar {
            pu/*caret*/
        }
    """)

    fun `test union`() = checkCompletion("union",
        "unio/*caret*/",
        "union /*caret*/"
    )

    fun `test pub union`() = checkCompletion("union",
        "pub unio/*caret*/",
        "pub union /*caret*/"
    )

    fun `test no union in expr`() = checkNoCompletion("""
        fn foo() {
            let x = 42 + unio/*caret*/;
        }
    """)

    fun `test no return in struct literal`() = checkNotContainsCompletion("return", """
        struct S { a: u32, b: u32 }
        fn foo() {
            let s = S { /*caret*/ };
        }
    """)

    fun `test no let in struct literal`() = checkNotContainsCompletion("let", """
        struct S { a: u32, b: u32 }
        fn foo() {
            let s = S { /*caret*/ };
        }
    """)

    fun `test no return in struct pat`() = checkNotContainsCompletion("return", """
        struct S { a: u32, b: u32 }
        fn foo(s: S) {
            match s {
                S { /*caret*/ } => {}
            }
        }
    """)

    fun `test no let in struct pat`() = checkNotContainsCompletion("let", """
        struct S { a: u32, b: u32 }
        fn foo(s: S) {
            match s {
                S { /*caret*/ } => {}
            }
        }
    """)

    // Smart mode is used for not completion tests to disable additional results
    // from language agnostic `com.intellij.codeInsight.completion.WordCompletionContributor`
    override fun checkNoCompletion(@Language("Rust") code: String) {
        val dumbService = DumbServiceImpl.getInstance(project)
        val oldValue = dumbService.isDumb
        try {
            dumbService.isDumb = false
            super.checkNoCompletion(code)
        } finally {
            dumbService.isDumb = oldValue
        }
    }

    private fun checkCompletion(
        lookupStrings: List<String>,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        for (lookupString in lookupStrings) {
            checkCompletion(lookupString, before, after.replace("/*lookup*/", lookupString))
        }
    }

    companion object {
        private val MEMBERS_KEYWORDS = listOf("fn", "type", "const", "unsafe")
    }
}
