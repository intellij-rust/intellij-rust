/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsModCompletionTest : RsCompletionTestBase() {
    fun testModCompletionSameDirectory() = doSingleCompletionMultiflie("""
    //- main.rs
        mod my_m/*caret*/

    //- my_mod.rs
        pub fn test() {}
    """, """
        mod my_mod/*caret*/
    """)

    fun testModCompletionSubdirectory() = doSingleCompletionMultiflie("""
    //- main.rs
        mod my_m/*caret*/;

    //- my_mod/mod.rs
        pub fn test() {}
    """, """
        mod my_mod/*caret*/;
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
