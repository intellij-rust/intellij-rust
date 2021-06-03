/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

class ExtractDependencySpecificationIntentionTest : CargoTomlIntentionTestBase(ExtractDependencySpecificationIntention::class) {
    fun `test availability range`() = checkAvailableInSelectionOnly("""
        [dependencies]
        <selection>foo = { version = "0.1.0", features = ["bar"] }</selection>
    """)

    fun `test unavailable in dependencies table`() = doUnavailableTest("""
        [dependencies]
        foo = "0.1.0"/*caret*/
    """)

    fun `test unavailable for deps tables`() = doUnavailableTest("""
        [dependencies.foo]
        version = "0.1.0"/*caret*/
        features = ["bar"]
    """)

    fun `test replace from name`() = doAvailableTest("""
        [dependencies]
        /*caret*/foo = { version = "0.1.0", features = ["bar"] }
    """, """
        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]/*caret*/
    """)

    fun `test replace simple`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.1.0" }/*caret*/
    """, """
        [dependencies.foo]
        version = "0.1.0"/*caret*/
    """)

    fun `test replace from value`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.1.0", features = ["bar"] }/*caret*/
    """, """
        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]/*caret*/
    """)

    fun `test replace from inside keyvalue`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.1.0", features = ["bar"/*caret*/] }
    """, """
        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]/*caret*/
    """)

    fun `test replace with platform-specific table`() = doAvailableTest("""
        [target.'cfg(unix)'.dependencies]
        foo = { version = "0.1.0", features = ["bar"] }/*caret*/
    """, """
        [target.'cfg(unix)'.dependencies.foo]
        version = "0.1.0"
        features = ["bar"]/*caret*/
    """)

    fun `test replace in the middle`() = doAvailableTest("""
        [dependencies]
        baz = "0.1.0"
        foo = { version = "0.1.0", features = ["bar"] }/*caret*/
        bar = { version = "0.2.0" }
    """, """
        [dependencies]
        baz = "0.1.0"
        bar = { version = "0.2.0" }

        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]/*caret*/
    """)

    fun `test replace with another block`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.1.0", features = ["bar"] }/*caret*/

        [features]
        something = []
    """, """
        [dependencies.foo]
        version = "0.1.0"
        features = ["bar"]/*caret*/

        [features]
        something = []
    """)

    fun `test replace in the middle with another block`() = doAvailableTest("""
        [dependencies]
        foo = { version = "0.0.1" }
        bar = { version = "0.0.2" }/*caret*/
        baz = { version = "0.0.3" }

        [dependencies.quux]
        version = "0.0.4"
    """, """
        [dependencies]
        foo = { version = "0.0.1" }
        baz = { version = "0.0.3" }

        [dependencies.bar]
        version = "0.0.2"

        [dependencies.quux]
        version = "0.0.4"
    """)
}
