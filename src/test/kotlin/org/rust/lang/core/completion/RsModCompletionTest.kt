/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsModCompletionTest : RsCompletionTestBase() {
    fun `test mod completion same directory`() = doSingleCompletionByFileTree("""
    //- main.rs
        mod my_m/*caret*/

    //- my_mod.rs
        pub fn test() {}
    """, """
        mod my_mod/*caret*/
    """)

    fun `test mod completion subdirectory`() = doSingleCompletionByFileTree("""
    //- main.rs
        mod my_m/*caret*/;

    //- my_mod/mod.rs
        pub fn test() {}
    """, """
        mod my_mod/*caret*/;
    """)

    fun `test mod completion same directory no mod rs`() = checkNoCompletionByFileTree("""
    //- function.rs
        mod mo/*caret*/;

        fn main() {}

    //- mod.rs
        pub fn test() {}
    """)

    fun `test mod completion same directory no main rs`() = checkNoCompletionByFileTree("""
    //- main.rs
        mod m/*caret*/;

        fn main() {}
    """)
}
