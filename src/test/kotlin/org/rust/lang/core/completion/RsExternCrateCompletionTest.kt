/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsExternCrateCompletionTest : RsCompletionTestBase() {
    fun `test extern crate`() = doSingleCompletion(
        "extern crate dep_l/*caret*/",
        "extern crate dep_lib_target/*caret*/"
    )

    fun `test extern crate does not suggest core`() = checkNoCompletion("""
        extern crate cor/*caret*/
    """)

    fun `test extern crate does not suggest our crate`() = checkNoCompletion("""
    //- lib.rs
        extern crate tes/*caret*/
    """)

    fun `test complete lib target of our package`() = doSingleCompletionByFileTree("""
    //- lib.rs
    //- main.rs
        extern crate tes/*caret*/
    """, """
        extern crate test_package/*caret*/
    """)

    fun `test extern crate does not suggest transitive dependency`() = checkNoCompletion("""
        extern crate trans_l/*caret*/
    """)

    fun `test absolute path suggest dependency`() = checkContainsCompletionByFileTree("test_package", """
    //- lib.rs
    //- main.rs
        fn main() {
            ::test_packa/*caret*/
        }
    """)

    fun `test absolute path suggest std`() = checkContainsCompletionByFileTree("std", """
    //- lib.rs
    //- main.rs
        fn main() {
            ::st/*caret*/
        }
    """)

    fun `test absolute path suggest aliased extern crate`() = checkContainsCompletionByFileTree(
        listOf("test_package", "test_package2"), """
    //- lib.rs
    //- main.rs
        extern crate test_package as test_package2;
        fn main() {
            ::test_packa/*caret*/
        }
    """)

    fun `test absolute path don't suggest self`() = checkNoCompletionByFileTree("""
    //- lib.rs
        fn main() {
            ::self/*caret*/
        }
    """)
}
