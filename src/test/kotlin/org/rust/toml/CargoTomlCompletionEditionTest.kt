/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CargoTomlCompletionEditionTest(private val sectionName: String) : CargoTomlCompletionTestBase() {

    @Before
    fun before() = setUp()

    @After
    fun after() = tearDown()

    @Test
    fun test() {
        doSingleCompletion("""
        $sectionName
        edi<caret>
    """, """
        $sectionName
        edition<caret>
    """
        )
    }

    companion object {
        @Parameterized.Parameters(name = "{index}: 'edition' key completion in \"{0}\" cargo toml section")
        @JvmStatic fun data() = listOf(
            arrayOf("[package]"),
            arrayOf("[lib]"),
            arrayOf("[[bench]]"),
            arrayOf("[[test]]"),
            arrayOf("[[bin]]"),
            arrayOf("[[example]]")
        )
    }
}
