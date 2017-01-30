package org.rust.lang.core.completion

class RsModCompletionTest : RsCompletionTestBase() {
    fun testModCompletionSameDirectory() = checkSingleCompletionWithMultipleFiles("my_mod", """
    //- main.rs
        mod my_m/*caret*/;

        fn main() {}

    //- my_mod.rs
        pub fn test() {}
    """)

    fun testModCompletionSubdirectory() = checkSingleCompletionWithMultipleFiles("my_mod", """
    //- main.rs
        mod my_m/*caret*/;

        fn main() {}

    //- my_mod/mod.rs
        pub fn test() {}
    """)

    fun testModCompletionSameDirectoryNoModRS() = checkNoCompletionWithMultipleFiles("""
    //- function.rs
        mod mo/*caret*/;

        fn main() {}

    //- mod.rs
        pub fn test() {}
    """)

    fun testModCompletionSameDirectoryNoMainRS() = checkNoCompletionWithMultipleFiles("""
    //- main.rs
        mod m/*caret*/;

        fn main() {}
    """)
}
