/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

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
    fun `key completion`() = doSingleCompletion("""
        $sectionName
        edi<caret>
    """, """
        $sectionName
        edition<caret>
    """)

    @Test
    fun `value 2018 completion without quotes`() = doSingleCompletion("""
        $sectionName
        edition = 8<caret>
    """, """
        $sectionName
        edition = "2018<caret>"
    """)

    @Test
    fun `value 2018 completion with quotes`() = doSingleCompletion("""
        $sectionName
        edition = "8<caret>"
    """, """
        $sectionName
        edition = "2018<caret>"
    """)

    @Test
    fun `value 2015 completion without quotes`() = doSingleCompletion("""
        $sectionName
        edition = 5<caret>
    """, """
        $sectionName
        edition = "2015<caret>"
    """)

    @Test
    fun `value 2015 completion with quotes`() = doSingleCompletion("""
        $sectionName
        edition = "5<caret>"
    """, """
        $sectionName
        edition = "2015<caret>"
    """)

    @Test
    fun `other values are not completed`() = checkNoCompletion("""
        $sectionName
        edition = "3<caret>"
    """)

    @Test
    fun `values for different keys are not completed`() = checkNoCompletion("""
        $sectionName
        edidition = "8<caret>"
    """)

    companion object {
        @Parameterized.Parameters(name = "{index}: 'edition' completion in \"{0}\" cargo toml section")
        @JvmStatic
        fun data() = listOf(
            arrayOf("[package]"),
            arrayOf("[lib]"),
            arrayOf("[[bench]]"),
            arrayOf("[[test]]"),
            arrayOf("[[bin]]"),
            arrayOf("[[example]]")
        )
    }
}
