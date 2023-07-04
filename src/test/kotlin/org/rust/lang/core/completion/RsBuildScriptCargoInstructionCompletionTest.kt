/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsBuildScriptCargoInstructionCompletionTest : RsCompletionTestBase() {

    fun `test complete cargo instruction prefix`() = doSingleCompletionByFileTree("""
    //- build.rs
        fn main() {
            println!("ca/*caret*/");
        }
    """, """
        fn main() {
            println!("cargo:/*caret*/");
        }
    """)

    fun `test no completion outside macro`() = checkNoCompletionByFileTree("""
    //- build.rs
        const C: &str = "ca/*caret*/";
        fn main() {}
    """)

    fun `test no completion outside println macro`() = checkNoCompletionByFileTree("""
    //- build.rs
        fn main() {
            format!("ca/*caret*/");
        }
    """)

    fun `test no completion not in first position`() = checkNoCompletionByFileTree("""
    //- build.rs
        fn main() {
            println!("{}", "ca/*caret*/");
        }
    """)

    fun `test no completion not in build script`() = checkNoCompletionByFileTree("""
    //- main.rs
        fn main() {
            println!("ca/*caret*/");
        }
    """)

    fun `test complete cargo instruction`() = doSingleCompletionByFileTree("""
    //- build.rs
        fn main() {
            println!("cargo:rustc-fla/*caret*/");
        }
    """, """
        fn main() {
            println!("cargo:rustc-flags=/*caret*/");
        }
    """)

    fun `test do not add extra =`() = doSingleCompletionByFileTree("""
    //- build.rs
        fn main() {
            println!(r#"cargo:rustc-link-sear/*caret*/="#);
        }
    """, """
        fn main() {
            println!(r#"cargo:rustc-link-search/*caret*/="#);
        }
    """)
}
