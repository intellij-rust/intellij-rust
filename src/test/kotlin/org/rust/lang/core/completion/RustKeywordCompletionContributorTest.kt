package org.rust.lang.core.completion

class RustKeywordCompletionContributorTest : RustCompletionTestBase() {
    override val dataPath = ""

    fun testEnum() = checkSingleCompletion("enum", """
        enu/*caret*/
    """)

    fun testEnumAtTheFileVeryBeginning() = checkSingleCompletion("enum", "enu/*caret*/")

    fun testEnumWithinMod() = checkSingleCompletion("enum", """
        mod foo {
            en/*caret*/
        }
    """)

    fun testEnumNotAppliedWithinStruct() = checkNoCompletion("""
        struct Foo {
            en/*caret*/
        }
    """)

    fun testEnumNotAppliedIfDoesntStartExpression() = checkNoCompletion("""
        mod en/*caret*/
    """)

    fun testExternCrate() = checkSingleCompletion("extern crate", """
        exte/*caret*/
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

    fun testStruct() = checkSingleCompletion("struct", """
        str/*caret*/
    """)

    fun testTrait() = checkSingleCompletion("trait", """
        tra/*caret*/
    """)

    fun testType() = checkSingleCompletion("type", """
        typ/*caret*/
    """)

    fun testUse() = checkSingleCompletion("use", """
        us/*caret*/
    """)
}
