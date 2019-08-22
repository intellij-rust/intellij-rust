/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.MinRustcVersion
import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.stdext.BothEditions

@BothEditions
@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsExternCrateCompletionTest : RsCompletionTestBase() {
    fun `test extern crate`() = doSingleCompletion(
        "extern crate dep_l/*caret*/",
        "extern crate dep_lib_target/*caret*/"
    )

    fun `test does not suggest std`() = checkNoCompletion("""
        extern crate st/*caret*/
    """)

    fun `test suggest std if no_std`() = doSingleCompletion(
        "#![no_std] extern crate td/*caret*/",
        "#![no_std] extern crate std/*caret*/"
    )

    fun `test does not suggest core`() = checkNoCompletion("""
        extern crate cor/*caret*/
    """)

    fun `test suggest core if no_std`() = doSingleCompletion(
        "#![no_std] extern crate cor/*caret*/",
        "#![no_std] extern crate core/*caret*/"
    )

    fun `test does not suggest core if no_core`() = checkNoCompletion("""
        #![no_core]
        extern crate cor/*caret*/
    """)

    fun `test does not suggest our crate`() = checkNoCompletion("""
    //- lib.rs
        extern crate tes/*caret*/
    """)

    fun `test complete lib target of our package`() = doSingleCompletionByFileTree("""
    //- lib.rs
        #![placeholder]
    //- main.rs
        extern crate tes/*caret*/
    """, """
        extern crate test_package/*caret*/
    """)

    fun `test does not suggest transitive dependency`() = checkNoCompletion("""
        extern crate trans_l/*caret*/
    """)

    @MinRustcVersion("1.36.0")
    fun `test proc_macro`() = doSingleCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        extern crate proc_/*caret*/
    """, """
        extern crate proc_macro/*caret*/
    """)
}
