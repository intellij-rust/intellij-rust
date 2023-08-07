/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

class SimplifyDependencySpecificationIntentionTest : CargoTomlIntentionTestBase(SimplifyDependencySpecificationIntention::class) {
    fun `test availability range`() = checkAvailableInSelectionOnly("""
        [dependencies]
        <selection>foo = { version = "0.1.0" }</selection>
    """)

    fun `test unavailable for deps with string value`() = doUnavailableTest("""
        [dependencies]
        foo = "0.1.0"<caret>
    """)

    fun `test unavailable with multiple properties`() = doUnavailableTest("""
        [dependencies]
        foo = { version = "0.1.0", features = [] }<caret>
    """)

    fun `test unavailable without version property`() = doUnavailableTest("""
        [dependencies]
        foo = { features = [] }<caret>
    """)

    fun `test unavailable in other tables`() = doUnavailableTest("""
        [foo]
        bar = { version = "0.1.0" }<caret>
    """)

    fun `test replace from key`() = doAvailableTest("""
        [dependencies]
        <caret>foo = { version = "0.1.0" }
    """, """
        [dependencies]
        foo = "0.1.0"<caret>
    """)

    fun `test replace from inside value`() = doAvailableTest("""
        [dependencies]
        foo = { version<caret> = "0.1.0" }
    """, """
        [dependencies]
        foo = "0.1.0"<caret>
    """)
}
