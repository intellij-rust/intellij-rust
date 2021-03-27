/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

class RsSSTypeTest : RsSSTestBase() {
    fun `test lifetime quote`() = doTest("""
        <warning>struct S(&'a u32);</warning>
        <warning>struct S(&'b u32);</warning>
    """, """struct '_(&\''_ u32)""")
}
