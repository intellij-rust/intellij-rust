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
    fun keyCompletion() {
        doSingleCompletion("""
        $sectionName
        edi<caret>
    """, """
        $sectionName
        edition<caret>
    """
        )
    }

    @Test
    fun value2018CompletionWithoutQuotes() {
        doSingleCompletion("""
        $sectionName
        edition = 8<caret>
    """, """
        $sectionName
        edition = "2018"
    """)
    }

    @Test
    fun value2018CompletionWithQuotes() {
        doSingleCompletion("""
        $sectionName
        edition = "8<caret>"
    """, """
        $sectionName
        edition = "2018"
    """)
    }

    @Test
    fun value2015CompletionWithoutQuotes() {
        doSingleCompletion("""
        $sectionName
        edition = 5<caret>
    """, """
        $sectionName
        edition = "2015"
    """)
    }

    @Test
    fun value2015CompletionWithQuotes() {
        doSingleCompletion("""
        $sectionName
        edition = "5<caret>"
    """, """
        $sectionName
        edition = "2015"
    """)
    }

    @Test
    fun otherValuesAreNotCompleted() {
        checkNoCompletion("""
        $sectionName
        edition = "3<caret>"
    """)
    }

    @Test
    fun valuesForDifferentKeyAreNotCompleted() {
        checkNoCompletion("""
        $sectionName
        edidition = "8<caret>"
    """)
    }

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
