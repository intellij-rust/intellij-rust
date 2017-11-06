/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.lang.core.completion.RsKeywordCompletionContributor.Companion.CONDITION_KEYWORDS

class RsKeywordCompletionContributorTest : RsCompletionTestBase() {
    fun `test break in for loop`() = @Suppress("DEPRECATION") checkSingleCompletion("break", """
        fn foo() {
            for _ in 0..4 {
                bre/*caret*/
            }
        }
    """)

    fun `test break in loop`() = @Suppress("DEPRECATION") checkSingleCompletion("break", """
        fn foo() {
            loop {
                br/*caret*/
            }
        }
    """)

    fun `test break in while loop`() = @Suppress("DEPRECATION") checkSingleCompletion("break", """
        fn foo() {
            while true {
                brea/*caret*/
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

    fun `test continue in for loop`() = @Suppress("DEPRECATION") checkSingleCompletion("continue", """
        fn foo() {
            for _ in 0..4 {
                cont/*caret*/
            }
        }
    """)

    fun `test continue in loop`() = @Suppress("DEPRECATION") checkSingleCompletion("continue", """
        fn foo() {
            loop {
                cont/*caret*/
            }
        }
    """)

    fun `test continue in while loop`() = @Suppress("DEPRECATION") checkSingleCompletion("continue", """
        fn foo() {
            while true {
                conti/*caret*/
            }
        }
    """)

    fun `test const`() = @Suppress("DEPRECATION") checkSingleCompletion("const", """
        con/*caret*/
    """)

    fun `test pub const`() = @Suppress("DEPRECATION") checkSingleCompletion("const", """
        pub con/*caret*/
    """)

    fun `test enum`() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        enu/*caret*/
    """)

    fun `test enum at the file very beginning`() = @Suppress("DEPRECATION") checkSingleCompletion("enum", "enu/*caret*/")

    fun `test pub enum`() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        pub enu/*caret*/
    """)

    fun `test enum within mod`() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        mod foo {
            en/*caret*/
        }
    """)

    fun `test enum within fn`() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        fn foo() {
            en/*caret*/
        }
    """)

    fun `test enum within fn nested block`() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        fn foo() {{
            en/*caret*/
        }}
    """)

    fun `test enum within fn after other stmt`() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        fn foo() {
            let _ = 10;
            en/*caret*/
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

    fun `test extern`() = @Suppress("DEPRECATION") checkSingleCompletion("extern", """
        ext/*caret*/
    """)

    fun `test pub extern`() = @Suppress("DEPRECATION") checkSingleCompletion("extern", """
        pub ext/*caret*/
    """)

    fun `test unsafe extern`() = @Suppress("DEPRECATION") checkSingleCompletion("extern", """
        unsafe ext/*caret*/
    """)

    fun `test pub unsafe extern`() = @Suppress("DEPRECATION") checkSingleCompletion("extern", """
        pub unsafe ext/*caret*/
    """)

    fun `test extern crate`() = @Suppress("DEPRECATION") checkSingleCompletion("crate", """
        extern cr/*caret*/
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

    fun `test extern fn`() = @Suppress("DEPRECATION") checkSingleCompletion("fn", """
        extern f/*caret*/
    """)

    fun `test unsafe fn`() = @Suppress("DEPRECATION") checkSingleCompletion("fn", """
        unsafe f/*caret*/
    """)

    fun `test impl`() = @Suppress("DEPRECATION") checkSingleCompletion("impl", """
        imp/*caret*/
    """)

    fun `test unsafe impl`() = @Suppress("DEPRECATION") checkSingleCompletion("impl", """
        unsafe im/*caret*/
    """)

    fun `test let within fn`() = @Suppress("DEPRECATION") checkSingleCompletion("let", """
        fn main() {
            let a = 12;
            le/*caret*/
        }
    """)

    fun `test let within assoc fn`() = @Suppress("DEPRECATION") checkSingleCompletion("let", """
        struct Foo;
        impl Foo {
            fn shutdown() { le/*caret*/ }
        }
    """)

    fun `test let within method`() = @Suppress("DEPRECATION") checkSingleCompletion("let", """
        struct Foo;
        impl Foo {
            fn calc(&self) { le/*caret*/ }
        }
    """)

    fun `test let not applied within nested mod`() = checkNoCompletion("""
        fn foo() {
            mod bar {
                le/*caret*/
            }
        }
    """)

    fun `test mod`() = @Suppress("DEPRECATION") checkSingleCompletion("mod", """
        mo/*caret*/
    """)

    fun `test pub mod`() = @Suppress("DEPRECATION") checkSingleCompletion("mod", """
        pub mo/*caret*/
    """)

    fun `test mut`() = @Suppress("DEPRECATION") checkSingleCompletion("mut", """
        fn main() {
            let m/*caret*/
        }
    """)

    fun `test return within fn`() = @Suppress("DEPRECATION") checkSingleCompletion("return", """
        fn main() {
            re/*caret*/
        }
    """)

    fun `test return within assoc fn`() = @Suppress("DEPRECATION") checkSingleCompletion("return", """
        struct Foo;
        impl Foo {
            fn shutdown() { retu/*caret*/ }
        }
    """)

    fun `test return within method`() = @Suppress("DEPRECATION") checkSingleCompletion("return", """
        struct Foo;
        impl Foo {
            fn print(&self) { retu/*caret*/ }
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

    fun `test static`() = @Suppress("DEPRECATION") checkSingleCompletion("static", """
        sta/*caret*/
    """)

    fun `test pub static`() = @Suppress("DEPRECATION") checkSingleCompletion("static", """
        pub stat/*caret*/
    """)

    fun `test struct`() = @Suppress("DEPRECATION") checkSingleCompletion("struct", """
        str/*caret*/
    """)

    fun `test pub struct`() = @Suppress("DEPRECATION") checkSingleCompletion("struct", """
        pub str/*caret*/
    """)

    fun `test trait`() = @Suppress("DEPRECATION") checkSingleCompletion("trait", """
        tra/*caret*/
    """)

    fun `test pub trait`() = @Suppress("DEPRECATION") checkSingleCompletion("trait", """
        pub tra/*caret*/
    """)

    fun `test unsafe trait`() = @Suppress("DEPRECATION") checkSingleCompletion("trait", """
        unsafe tra/*caret*/
    """)

    fun `test type`() = @Suppress("DEPRECATION") checkSingleCompletion("type", """
        typ/*caret*/
    """)

    fun `test pub type`() = @Suppress("DEPRECATION") checkSingleCompletion("type", """
        pub typ/*caret*/
    """)

    fun `test unsafe`() = @Suppress("DEPRECATION") checkSingleCompletion("unsafe", """
        uns/*caret*/
    """)

    fun `test pub unsafe`() = @Suppress("DEPRECATION") checkSingleCompletion("unsafe", """
        pub unsa/*caret*/
    """)

    fun `test use`() = @Suppress("DEPRECATION") checkSingleCompletion("use", """
        us/*caret*/
    """)

    fun `test pub use`() = @Suppress("DEPRECATION") checkSingleCompletion("use", """
        pub us/*caret*/
    """)

    fun `test use self`() = @Suppress("DEPRECATION") checkSingleCompletion("self::", """
        use se/*caret*/
    """)

    fun `test use super`() = @Suppress("DEPRECATION") checkSingleCompletion("super::", """
        mod m { use su/*caret*/ }
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

    fun `test where in generic function`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        fn foo<T>(t: T) whe/*caret*/
    """)

    fun `test where in generic function with ret type`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        fn foo<T>(t: T) -> i32 whe/*caret*/
    """)

    fun `test where in not generic function`() = checkNoCompletion("""
        fn foo() whe/*caret*/
    """)

    fun `test where in not generic function with ret type`() = checkNoCompletion("""
        fn foo() -> i32 whe/*caret*/
    """)

    fun `test where in trait method`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        trait Foo {
            fn foo() whe/*caret*/
        }
    """)

    fun `test where in method`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        impl Foo {
            fn foo() whe/*caret*/
        }
    """)

    fun `test where in generic struct`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        struct Foo<T> whe/*caret*/
    """)

    fun `test where in generic tuple struct`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        struct Foo<T>(T) whe/*caret*/
    """)

    fun `test where in not generic struct`() = checkNoCompletion("""
        struct Foo whe/*caret*/
    """)

    fun `test where in generic enum`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        enum Foo<T> whe/*caret*/
    """)

    fun `test where in not generic enum`() = checkNoCompletion("""
        enum Foo whe/*caret*/
    """)

    fun `test where in generic type alias`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        type Foo<T> whe/*caret*/
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

    fun `test where in trait`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        trait Foo whe/*caret*/
    """)

    fun `test where in generic trait`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        trait Foo<T> whe/*caret*/
    """)

    fun `test where in impl`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        impl Foo whe/*caret*/
    """)

    fun `test where in trait impl`() = @Suppress("DEPRECATION") checkSingleCompletion("where", """
        impl<T> Foo<T> for Bar whe/*caret*/
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

    private fun checkCompletion(
        lookupStrings: List<String>,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        for (lookupString in lookupStrings) {
            checkCompletion(lookupString, before, after.replace("/*lookup*/", lookupString))
        }
    }

    private fun checkCompletion(
        lookupString: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = checkByText(before, after) {
        val items = myFixture.completeBasic()
            ?: return@checkByText // single completion was inserted
        val lookupItem = items.find { it.lookupString == lookupString } ?: return@checkByText
        myFixture.lookup.currentItem = lookupItem
        myFixture.type('\n')
    }
}
