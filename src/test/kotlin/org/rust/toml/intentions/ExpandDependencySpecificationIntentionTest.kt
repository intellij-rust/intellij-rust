/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

class ExpandDependencySpecificationIntentionTest : CargoTomlIntentionTestBase(ExpandDependencySpecificationIntention::class) {
    fun `test availability range`() = checkAvailableInSelectionOnly("""
        [dependencies]
        <selection>foo = "0.1.0"</selection>
    """)

    fun `test unavailable for already expanded deps`() = doUnavailableTest("""
        [dependencies]
        foo = { version = "0.1.0" }<caret>
    """)

    fun `test unavailable in table specifications`() = doUnavailableTest("""
        [dependencies.foo]
        version = "0.1.0"<caret>
    """)

    fun `test replace from name`() = doAvailableTest("""
        [dependencies]
        <caret>foo = "0.1.0"
    """, """
        [dependencies]
        foo = { version = "0.1.0" }<caret>
    """)

    fun `test replace from version`() = doAvailableTest("""
        [dependencies]
        foo = "0.1.0"<caret>
    """, """
        [dependencies]
        foo = { version = "0.1.0" }<caret>
    """)
}
