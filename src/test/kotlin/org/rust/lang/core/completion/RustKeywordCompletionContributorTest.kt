package org.rust.lang.core.completion

class RustKeywordCompletionContributorTest : RustCompletionTestBase() {
    override val dataPath = ""

    fun testExternCrate() = checkSingleCompletion("extern crate", """
        exte/*caret*/
    """)

    fun testExternCrateWithinMod() = checkSingleCompletion("extern crate", """
        mod foo {
            ex/*caret*/
        }
    """)

    fun testExternCrateNotAppliedWithinStruct() = checkNoCompletion("""
        struct Foo {
            ext/*caret*/
        }
    """)

    fun testExternCrateNotAppliedIfDoesntStartExpression() = checkNoCompletion("""
        mod ext/*caret*/
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
            fn shutdown() { l/*caret*/ }
        }
    """)

    fun testLetWithinMethod() = checkSingleCompletion("let", """
        struct Foo;
        impl Foo {
            fn calc(&self) { le/*caret*/ }
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

    fun testUse() = checkSingleCompletion("use", """
        u/*caret*/
    """)

    fun testUseWithinMod() = checkSingleCompletion("use", """
        mod foo {
            us/*caret*/
        }
    """)

    fun testUseNotAppliedWithinStruct() = checkNoCompletion("""
        struct Foo {
            us/*caret*/
        }
    """)

    fun testUseNotAppliedIfDoesntStartExpression() = checkNoCompletion("""
        const us/*caret*/
    """)

}
