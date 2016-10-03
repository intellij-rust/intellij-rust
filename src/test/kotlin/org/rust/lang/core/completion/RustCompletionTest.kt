package org.rust.lang.core.completion

class RustCompletionTest : RustCompletionTestBase() {

    override val dataPath = "org/rust/lang/core/completion/fixtures"

    fun testLocalVariable() = checkSoleCompletion()
    fun testFunctionName() = checkSoleCompletion()
    fun testPath() = checkSoleCompletion()
    fun testAnonymousItem() = checkSoleCompletion()
    fun testIncompleteLet() = checkSoleCompletion()
    fun testUseGlob() = checkSoleCompletion()
    fun testImplMethodType() = checkSoleCompletion()
    fun testStructField() = checkSoleCompletion()
    fun testIncompleteStructField() = checkSoleCompletion()
    fun testEnumField() = checkSoleCompletion()
    fun testWildcardImports() = checkSoleCompletion()
    fun testShadowing() = checkSoleCompletion()

    fun testLocalScope() = checkNoCompletion("""
        fn foo() {
            let x = spam/*caret*/;
            let spamlot = 92;
        }
    """)

    fun testWhileLet() = checkNoCompletion("""
        fn main() {
            while let Some(quazimagnitron) = quaz/*caret*/ { }
        }
    """)

    fun testCompleteAlias() = checkSoleCompletion()

    fun testAliasShadowsOriginalName() = checkNoCompletion("""
        mod m {
            pub fn transmogrify() {}
        }

        use self::m::{transmogrify as frobnicate};

        fn main() {
            trans/*caret*/
        }
    """)

    fun testCompleteSelfType() = checkSoleCompletion()

    fun testCompletionRespectsNamespaces() = checkNoCompletion("""
        fn foobar() {}

        fn main() {
            let _: f/*caret*/ = unimplemented!();
        }
    """)

    fun testChildFile() = checkByDirectory {
        openFileInEditor("main.rs")
        executeSoloCompletion()
    }

    fun testParentFile() = checkByDirectory {
        openFileInEditor("foo.rs")
        executeSoloCompletion()
    }

    fun testParentFile2() = checkByDirectory {
        openFileInEditor("foo/mod.rs")
        executeSoloCompletion()
    }
}
