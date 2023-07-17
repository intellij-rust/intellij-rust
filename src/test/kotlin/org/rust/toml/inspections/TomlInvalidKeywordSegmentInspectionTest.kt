/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

class TomlInvalidKeywordSegmentInspectionTest : CargoTomlCrateInspectionTestBase(TomlInvalidKeywordSegmentInspection::class) {
    fun `test not more than 5 keywords`() = doTest("""
        keywords = []
        keywords = ["a"]
        keywords = ["a", "a", "a", "a", "a"]
        keywords = <error descr="There can be maximum 5 keywords in the section">["a", "a", "a", "a", "a", "a"]</error>
    """)

    fun `test keyword should start with letter`() = doTest("""
        keywords = ["ab", <error descr="Keyword must be ASCII text, start with a letter, and only contain letters, numbers, _ or -, and have at most 20 characters.">"1b"</error>, <error descr="Keyword must be ASCII text, start with a letter, and only contain letters, numbers, _ or -, and have at most 20 characters.">""</error>]
    """)

    fun `test keyword should have at most 20 characters`() = doTest("""
        keywords = ["aaaaaaaaaaaaaaaaaa", "aaaaaaaaaaaaaaaaaaaa"]
    """)

    fun `test keyword should not contain forbidden characters`() = doTest("""
        keywords = ["a-_1", <error descr="Keyword must be ASCII text, start with a letter, and only contain letters, numbers, _ or -, and have at most 20 characters.">"a-_1%"</error>]
    """)

}
