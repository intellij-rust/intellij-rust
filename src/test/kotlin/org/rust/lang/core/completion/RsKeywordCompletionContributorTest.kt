/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.lang.core.completion.RsKeywordCompletionContributor.Companion.CONDITION_KEYWORDS

class RsKeywordCompletionContributorTest : RsCompletionTestBase() {
    fun testBreakInForLoop() = @Suppress("DEPRECATION") checkSingleCompletion("break", """
        fn foo() {
            for _ in 0..4 {
                bre/*caret*/
            }
        }
    """)

    fun testBreakInLoop() = @Suppress("DEPRECATION") checkSingleCompletion("break", """
        fn foo() {
            loop {
                br/*caret*/
            }
        }
    """)

    fun testBreakInWhileLoop() = @Suppress("DEPRECATION") checkSingleCompletion("break", """
        fn foo() {
            while true {
                brea/*caret*/
            }
        }
    """)

    fun testBreakNotAppliedIfDoesntStartStmt() = checkNoCompletion("""
        fn foo() {
            while true {
                let brea/*caret*/
            }
        }
    """)

    fun testBreakNotAppliedOutsideLoop() = checkNoCompletion("""
        fn foo() {
            bre/*caret*/
        }
    """)

    fun testBreakNotAppliedWithinClosure() = checkNoCompletion("""
        fn bar() {
            loop {
                let _ = || { bre/*caret*/ }
            }
        }
    """)

    fun testContinueInForLoop() = @Suppress("DEPRECATION") checkSingleCompletion("continue", """
        fn foo() {
            for _ in 0..4 {
                cont/*caret*/
            }
        }
    """)

    fun testContinueInLoop() = @Suppress("DEPRECATION") checkSingleCompletion("continue", """
        fn foo() {
            loop {
                cont/*caret*/
            }
        }
    """)

    fun testContinueInWhileLoop() = @Suppress("DEPRECATION") checkSingleCompletion("continue", """
        fn foo() {
            while true {
                conti/*caret*/
            }
        }
    """)

    fun testConst() = @Suppress("DEPRECATION") checkSingleCompletion("const", """
        con/*caret*/
    """)

    fun testPubConst() = @Suppress("DEPRECATION") checkSingleCompletion("const", """
        pub con/*caret*/
    """)

    fun testEnum() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        enu/*caret*/
    """)

    fun testEnumAtTheFileVeryBeginning() = @Suppress("DEPRECATION") checkSingleCompletion("enum", "enu/*caret*/")

    fun testPubEnum() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        pub enu/*caret*/
    """)

    fun testEnumWithinMod() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        mod foo {
            en/*caret*/
        }
    """)

    fun testEnumWithinFn() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        fn foo() {
            en/*caret*/
        }
    """)

    fun testEnumWithinFnNestedBlock() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        fn foo() {{
            en/*caret*/
        }}
    """)

    fun testEnumWithinFnAfterOtherStmt() = @Suppress("DEPRECATION") checkSingleCompletion("enum", """
        fn foo() {
            let _ = 10;
            en/*caret*/
        }
    """)

    fun testEnumNotAppliedIfDoesntStartStmtWithinFn() = checkNoCompletion("""
        fn foo() {
            let en/*caret*/
        }
    """)

    fun testEnumNotAppliedWithinStruct() = checkNoCompletion("""
        struct Foo {
            en/*caret*/
        }
    """)

    fun testEnumNotAppliedIfDoesntStartStmt() = checkNoCompletion("""
        mod en/*caret*/
    """)

    fun testExtern() = @Suppress("DEPRECATION") checkSingleCompletion("extern", """
        ext/*caret*/
    """)

    fun testPubExtern() = @Suppress("DEPRECATION") checkSingleCompletion("extern", """
        pub ext/*caret*/
    """)

    fun testUnsafeExtern() = @Suppress("DEPRECATION") checkSingleCompletion("extern", """
        unsafe ext/*caret*/
    """)

    fun testPubUnsafeExtern() = @Suppress("DEPRECATION") checkSingleCompletion("extern", """
        pub unsafe ext/*caret*/
    """)

    fun testExternCrate() = @Suppress("DEPRECATION") checkSingleCompletion("crate", """
        extern cr/*caret*/
    """)

    fun testCrateNotAppliedAtFileBeginning() = checkNoCompletion("crat/*caret*/")

    fun testCrateNotAppliedWithoutPrefix() = checkNoCompletion("""
        crat/*caret*/
    """)

    fun testFn() = checkContainsCompletion("fn", """
        f/*caret*/
    """)

    fun testPubFn() = checkContainsCompletion("fn", """
        pub f/*caret*/
    """)

    fun testExternFn() = @Suppress("DEPRECATION") checkSingleCompletion("fn", """
        extern f/*caret*/
    """)

    fun testUnsafeFn() = @Suppress("DEPRECATION") checkSingleCompletion("fn", """
        unsafe f/*caret*/
    """)

    fun testImpl() = @Suppress("DEPRECATION") checkSingleCompletion("impl", """
        imp/*caret*/
    """)

    fun testUnsafeImpl() = @Suppress("DEPRECATION") checkSingleCompletion("impl", """
        unsafe im/*caret*/
    """)

    fun testLetWithinFn() = @Suppress("DEPRECATION") checkSingleCompletion("let", """
        fn main() {
            let a = 12;
            le/*caret*/
        }
    """)

    fun testLetWithinAssocFn() = @Suppress("DEPRECATION") checkSingleCompletion("let", """
        struct Foo;
        impl Foo {
            fn shutdown() { le/*caret*/ }
        }
    """)

    fun testLetWithinMethod() = @Suppress("DEPRECATION") checkSingleCompletion("let", """
        struct Foo;
        impl Foo {
            fn calc(&self) { le/*caret*/ }
        }
    """)

    fun testLetNotAppliedWithinNestedMod() = checkNoCompletion("""
        fn foo() {
            mod bar {
                le/*caret*/
            }
        }
    """)

    fun testMod() = @Suppress("DEPRECATION") checkSingleCompletion("mod", """
        mo/*caret*/
    """)

    fun testPubMod() = @Suppress("DEPRECATION") checkSingleCompletion("mod", """
        pub mo/*caret*/
    """)

    fun testMut() = @Suppress("DEPRECATION") checkSingleCompletion("mut", """
        fn main() {
            let m/*caret*/
        }
    """)

    fun testReturnWithinFn() = @Suppress("DEPRECATION") checkSingleCompletion("return", """
        fn main() {
            re/*caret*/
        }
    """)

    fun testReturnWithinAssocFn() = @Suppress("DEPRECATION") checkSingleCompletion("return", """
        struct Foo;
        impl Foo {
            fn shutdown() { retu/*caret*/ }
        }
    """)

    fun testReturnWithinMethod() = @Suppress("DEPRECATION") checkSingleCompletion("return", """
        struct Foo;
        impl Foo {
            fn print(&self) { retu/*caret*/ }
        }
    """)

    fun testReturnNotAppliedOnFileLevel() = checkNoCompletion("""
        retu/*caret*/
    """)

    fun testReturnNotAppliedWithinParametersList() = checkNoCompletion("""
        fn foo(retu/*caret*/) {}
    """)

    fun testReturnNotAppliedBeforeBlock() = checkNoCompletion("""
        fn foo() retu/*caret*/ {}
    """)

    fun testReturnNotAppliedIfDoesntStartStatement() = checkNoCompletion("""
        const retu/*caret*/
    """)

    fun testStatic() = @Suppress("DEPRECATION") checkSingleCompletion("static", """
        sta/*caret*/
    """)

    fun testPubStatic() = @Suppress("DEPRECATION") checkSingleCompletion("static", """
        pub stat/*caret*/
    """)

    fun testStruct() = @Suppress("DEPRECATION") checkSingleCompletion("struct", """
        str/*caret*/
    """)

    fun testPubStruct() = @Suppress("DEPRECATION") checkSingleCompletion("struct", """
        pub str/*caret*/
    """)

    fun testTrait() = @Suppress("DEPRECATION") checkSingleCompletion("trait", """
        tra/*caret*/
    """)

    fun testPubTrait() = @Suppress("DEPRECATION") checkSingleCompletion("trait", """
        pub tra/*caret*/
    """)

    fun testUnsafeTrait() = @Suppress("DEPRECATION") checkSingleCompletion("trait", """
        unsafe tra/*caret*/
    """)

    fun testType() = @Suppress("DEPRECATION") checkSingleCompletion("type", """
        typ/*caret*/
    """)

    fun testPubType() = @Suppress("DEPRECATION") checkSingleCompletion("type", """
        pub typ/*caret*/
    """)

    fun testUnsafe() = @Suppress("DEPRECATION") checkSingleCompletion("unsafe", """
        uns/*caret*/
    """)

    fun testPubUnsafe() = @Suppress("DEPRECATION") checkSingleCompletion("unsafe", """
        pub unsa/*caret*/
    """)

    fun testUse() = @Suppress("DEPRECATION") checkSingleCompletion("use", """
        us/*caret*/
    """)

    fun testPubUse() = @Suppress("DEPRECATION") checkSingleCompletion("use", """
        pub us/*caret*/
    """)

    fun testUseSelf() = @Suppress("DEPRECATION") checkSingleCompletion("self::", """
        use se/*caret*/
    """)

    fun testUseSuper() = @Suppress("DEPRECATION") checkSingleCompletion("super::", """
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

    fun `test if|match in start of expression`() = checkCompletion(CONDITION_KEYWORDS, """
        fn foo() {
            let x = /*caret*/
        }
    """, """
        fn foo() {
            let x = /*lookup*/ /*caret*/ { }
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
