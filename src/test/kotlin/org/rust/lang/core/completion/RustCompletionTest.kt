package org.rust.lang.core.completion

class RustCompletionTest : RustCompletionTestBase() {

    override val dataPath = "org/rust/lang/core/completion/fixtures"

    fun testLocalVariable() = checkSingleCompletion("quux", """
        fn foo(quux: i32) { qu/*caret*/ }
    """)

    fun testFunctionName() = checkSingleCompletion("frobnicate", """
        fn frobnicate() {}

        fn main() { frob/*caret*/ }
    """)

    fun testPath() = checkSingleCompletion("frobnicate", """
        mod foo {
            mod bar {
                fn frobnicate() {}
            }
        }

        fn frobfrobfrob() {}

        fn main() {
            foo::bar::frob/*caret*/
        }
    """)

    fun testAnonymousItemDoesNotBreakCompletion() = checkSingleCompletion("frobnicate", """
        extern "C" { }

        fn frobnicate() {}

        fn main() {
            frob/*caret*/
        }
    """)

    fun testUseGlob() = checkSingleCompletion("quux", """
        mod foo {
            pub fn quux() {}
        }

        use self::foo::{q/*caret*/};

        fn main() {}
    """)

    fun testWildcardImports() = checkSingleCompletion("transmogrify", """
        mod foo {
            fn transmogrify() {}
        }

        fn main() {
            use self::foo::*;

            trans/*caret*/
        }
    """)

    fun testShadowing() = checkSingleCompletion("foobar", """
        fn main() {
            let foobar = "foobar";
            let foobar = foobar.to_string();
            foo/*caret*/
        }
    """)

    fun testCompleteAlias() = checkSingleCompletion("frobnicate", """
        mod m {
            pub fn transmogrify() {}
        }

        use self::m::{transmogrify as frobnicate};

        fn main() {
            frob/*caret*/
        }
    """)

    fun testCompleteSelfType() = checkSingleCompletionByFile()
    fun testStructField() = checkSingleCompletionByFile()
    fun testEnumField() = checkSingleCompletionByFile()

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


    fun testAliasShadowsOriginalName() = checkNoCompletion("""
        mod m {
            pub fn transmogrify() {}
        }

        use self::m::{transmogrify as frobnicate};

        fn main() {
            trans/*caret*/
        }
    """)

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
