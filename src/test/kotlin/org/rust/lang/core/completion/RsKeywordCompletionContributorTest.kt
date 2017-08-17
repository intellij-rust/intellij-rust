/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.lang.core.completion.RsKeywordCompletionContributor.Companion.CONDITION_KEYWORDS

class RsKeywordCompletionContributorTest : RsCompletionTestBase() {
    fun testBreakInForLoop() = checkSingleCompletion("break", """
        fn foo() {
            for _ in 0..4 {
                bre/*caret*/
            }
        }
    """)

    fun testBreakInLoop() = checkSingleCompletion("break", """
        fn foo() {
            loop {
                br/*caret*/
            }
        }
    """)

    fun testBreakInWhileLoop() = checkSingleCompletion("break", """
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

    fun testContinueInForLoop() = checkSingleCompletion("continue", """
        fn foo() {
            for _ in 0..4 {
                cont/*caret*/
            }
        }
    """)

    fun testContinueInLoop() = checkSingleCompletion("continue", """
        fn foo() {
            loop {
                cont/*caret*/
            }
        }
    """)

    fun testContinueInWhileLoop() = checkSingleCompletion("continue", """
        fn foo() {
            while true {
                conti/*caret*/
            }
        }
    """)

    fun testConst() = checkSingleCompletion("const", """
        con/*caret*/
    """)

    fun testPubConst() = checkSingleCompletion("const", """
        pub con/*caret*/
    """)

    fun testEnum() = checkSingleCompletion("enum", """
        enu/*caret*/
    """)

    fun testEnumAtTheFileVeryBeginning() = checkSingleCompletion("enum", "enu/*caret*/")

    fun testPubEnum() = checkSingleCompletion("enum", """
        pub enu/*caret*/
    """)

    fun testEnumWithinMod() = checkSingleCompletion("enum", """
        mod foo {
            en/*caret*/
        }
    """)

    fun testEnumWithinFn() = checkSingleCompletion("enum", """
        fn foo() {
            en/*caret*/
        }
    """)

    fun testEnumWithinFnNestedBlock() = checkSingleCompletion("enum", """
        fn foo() {{
            en/*caret*/
        }}
    """)

    fun testEnumWithinFnAfterOtherStmt() = checkSingleCompletion("enum", """
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

    fun testExtern() = checkSingleCompletion("extern", """
        ext/*caret*/
    """)

    fun testPubExtern() = checkSingleCompletion("extern", """
        pub ext/*caret*/
    """)

    fun testUnsafeExtern() = checkSingleCompletion("extern", """
        unsafe ext/*caret*/
    """)

    fun testPubUnsafeExtern() = checkSingleCompletion("extern", """
        pub unsafe ext/*caret*/
    """)

    fun testExternCrate() = checkSingleCompletion("crate", """
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

    fun testExternFn() = checkSingleCompletion("fn", """
        extern f/*caret*/
    """)

    fun testUnsafeFn() = checkSingleCompletion("fn", """
        unsafe f/*caret*/
    """)

    fun testImpl() = checkSingleCompletion("impl", """
        imp/*caret*/
    """)

    fun testUnsafeImpl() = checkSingleCompletion("impl", """
        unsafe im/*caret*/
    """)

    fun testLetWithinFn() = checkSingleCompletion("let", """
        fn main() {
            let a = 12;
            le/*caret*/
        }
    """)

    fun testLetWithinAssocFn() = checkSingleCompletion("let", """
        struct Foo;
        impl Foo {
            fn shutdown() { le/*caret*/ }
        }
    """)

    fun testLetWithinMethod() = checkSingleCompletion("let", """
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

    fun testMod() = checkSingleCompletion("mod", """
        mo/*caret*/
    """)

    fun testPubMod() = checkSingleCompletion("mod", """
        pub mo/*caret*/
    """)

    fun testMut() = checkSingleCompletion("mut", """
        fn main() {
            let m/*caret*/
        }
    """)

    fun testReturnWithinFn() = checkSingleCompletion("return", """
        fn main() {
            re/*caret*/
        }
    """)

    fun testReturnWithinAssocFn() = checkSingleCompletion("return", """
        struct Foo;
        impl Foo {
            fn shutdown() { retu/*caret*/ }
        }
    """)

    fun testReturnWithinMethod() = checkSingleCompletion("return", """
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

    fun testStatic() = checkSingleCompletion("static", """
        sta/*caret*/
    """)

    fun testPubStatic() = checkSingleCompletion("static", """
        pub stat/*caret*/
    """)

    fun testStruct() = checkSingleCompletion("struct", """
        str/*caret*/
    """)

    fun testPubStruct() = checkSingleCompletion("struct", """
        pub str/*caret*/
    """)

    fun testTrait() = checkSingleCompletion("trait", """
        tra/*caret*/
    """)

    fun testPubTrait() = checkSingleCompletion("trait", """
        pub tra/*caret*/
    """)

    fun testUnsafeTrait() = checkSingleCompletion("trait", """
        unsafe tra/*caret*/
    """)

    fun testType() = checkSingleCompletion("type", """
        typ/*caret*/
    """)

    fun testPubType() = checkSingleCompletion("type", """
        pub typ/*caret*/
    """)

    fun testUnsafe() = checkSingleCompletion("unsafe", """
        uns/*caret*/
    """)

    fun testPubUnsafe() = checkSingleCompletion("unsafe", """
        pub unsa/*caret*/
    """)

    fun testUse() = checkSingleCompletion("use", """
        us/*caret*/
    """)

    fun testPubUse() = checkSingleCompletion("use", """
        pub us/*caret*/
    """)

    fun testUseSelf() = checkSingleCompletion("self::", """
        use se/*caret*/
    """)

    fun testUseSuper() = checkSingleCompletion("super::", """
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

    fun `test where in generic function`() = checkSingleCompletion("where", """
        fn foo<T>(t: T) whe/*caret*/
    """)

    fun `test where in generic function with ret type`() = checkSingleCompletion("where", """
        fn foo<T>(t: T) -> i32 whe/*caret*/
    """)

    fun `test where in not generic function`() = checkNoCompletion("""
        fn foo() whe/*caret*/
    """)

    fun `test where in not generic function with ret type`() = checkNoCompletion("""
        fn foo() -> i32 whe/*caret*/
    """)

    fun `test where in trait method`() = checkSingleCompletion("where", """
        trait Foo {
            fn foo() whe/*caret*/
        }
    """)

    fun `test where in method`() = checkSingleCompletion("where", """
        impl Foo {
            fn foo() whe/*caret*/
        }
    """)

    fun `test where in generic struct`() = checkSingleCompletion("where", """
        struct Foo<T> whe/*caret*/
    """)

    fun `test where in generic tuple struct`() = checkSingleCompletion("where", """
        struct Foo<T>(T) whe/*caret*/
    """)

    fun `test where in not generic struct`() = checkNoCompletion("""
        struct Foo whe/*caret*/
    """)

    fun `test where in generic enum`() = checkSingleCompletion("where", """
        enum Foo<T> whe/*caret*/
    """)

    fun `test where in not generic enum`() = checkNoCompletion("""
        enum Foo whe/*caret*/
    """)

    fun `test where in generic type alias`() = checkSingleCompletion("where", """
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

    fun `test where in trait`() = checkSingleCompletion("where", """
        trait Foo whe/*caret*/
    """)

    fun `test where in generic trait`() = checkSingleCompletion("where", """
        trait Foo<T> whe/*caret*/
    """)

    fun `test where in impl`() = checkSingleCompletion("where", """
        impl Foo whe/*caret*/
    """)

    fun `test where in trait impl`() = checkSingleCompletion("where", """
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
