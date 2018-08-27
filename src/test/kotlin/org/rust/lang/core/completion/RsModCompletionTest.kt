/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsModCompletionTest : RsCompletionTestBase() {
    fun `test mod completion same directory`() = doSingleCompletionMultifile("""
    //- main.rs
        mod my_m/*caret*/

    //- my_mod.rs
        pub fn test() {}
    """, """
        mod my_mod/*caret*/
    """)

    fun `test mod completion subdirectory`() = doSingleCompletionMultifile("""
    //- main.rs
        mod my_m/*caret*/;

    //- my_mod/mod.rs
        pub fn test() {}
    """, """
        mod my_mod/*caret*/;
    """)

    fun `test mod completion same directory no mod rs`() = checkNoCompletionWithMultifile("""
    //- function.rs
        mod mo/*caret*/;

        fn main() {}

    //- mod.rs
        pub fn test() {}
    """)

    fun `test mod completion same directory no main rs`() = checkNoCompletionWithMultifile("""
    //- main.rs
        mod m/*caret*/;

        fn main() {}
    """)
}
