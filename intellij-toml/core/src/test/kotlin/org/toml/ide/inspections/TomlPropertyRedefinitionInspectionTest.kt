/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.inspections

class TomlPropertyRedefinitionInspectionTest : TomlInspectionsTestBase(TomlPropertyRedefinitionInspection::class) {
    fun `test in keys`() = checkByText("""
        foo = "bar"
        <error descr="Property redefinition is not allowed">foo</error> = "baz"
    """)

    fun `test in table`() = checkByText("""
        foo.bar = 1

        [foo]
        <error descr="Property redefinition is not allowed">bar</error> = 2
    """)

    fun `test in inline table`() = checkByText("""
        foo.bar = 1
        foo = { <error descr="Property redefinition is not allowed">bar</error> = "" }
    """)
}
