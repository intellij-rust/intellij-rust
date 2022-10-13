/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsExternCrateCompletionTest : RsCompletionTestBase() {
    fun `test extern crate`() = doSingleCompletionByFileTree("""
    //- dep-lib/lib.rs
    //- lib.rs
        extern crate dep_l/*caret*/
    """, """
        extern crate dep_lib_target/*caret*/
    """)

    fun `test extern crate does not suggest core`() = checkNoCompletion("""
        extern crate cor/*caret*/
    """)

    fun `test extern crate does not suggest our crate`() = checkNoCompletionByFileTree("""
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
}
