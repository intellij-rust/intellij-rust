/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.testFramework.LightProjectDescriptor

class RsExternCrateCompletionTest : RsCompletionTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibAndDependencyRustProjectDescriptor

    fun `test extern crate`() = doSingleCompletion(
        "extern crate dep_l/*caret*/",
        "extern crate dep_lib_target/*caret*/"
    )

    fun `test extern crate does not suggest core`() = checkNoCompletion("""
        extern crate cor/*caret*/
    """)

    fun `test extern crate does not suggest our crate`() = checkNoCompletion("""
        extern crate tes/*caret*/
    """)

    fun `test extern crate does not suggest transitive dependency`() = checkNoCompletion("""
        extern crate trans_l/*caret*/
    """)
}
