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

        [<error descr="Property redefinition is not allowed">foo</error>]
        <error descr="Property redefinition is not allowed">bar</error> = 2
    """)

    fun `test in inline table`() = checkByText("""
        foo.bar = 1
        <error descr="Property redefinition is not allowed">foo</error> = { <error descr="Property redefinition is not allowed">bar</error> = "" }
    """)

    fun `test in table header 1`() = checkByText("""
        [foo]
        bar1 = 1

        [<error descr="Property redefinition is not allowed">foo</error>]
        bar2 = 2
    """)

    fun `test in table header 2`() = checkByText("""
        [foo.bar]
        bar1 = 1

        [<error descr="Property redefinition is not allowed">foo.bar</error>]
        bar2 = 2
    """)

    fun `test in inline table 2`() = checkByText("""
        foo = { bar = "" }

        [<error descr="Property redefinition is not allowed">foo</error>]
        <error descr="Property redefinition is not allowed">bar</error> = 1
    """)

    fun `test in array key`() = checkByText("""
        foo = 1

        [[<error descr="Property redefinition is not allowed">foo</error>]]
        baz = 2
    """)

    fun `test table and array`() = checkByText("""
        [foo.bar]
        bar = 1

        [[<error descr="Property redefinition is not allowed">foo</error>]]
        baz = 2
    """)

    fun `test no errors for arrays`() = checkByText("""
        [[foo]]
        bar1 = 1

        [[foo]]
        bar2 = 2
    """)
}
