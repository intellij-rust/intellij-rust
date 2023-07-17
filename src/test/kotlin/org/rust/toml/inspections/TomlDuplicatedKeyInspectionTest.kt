/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

class TomlDuplicatedKeyInspectionTest : CargoTomlCrateInspectionTestBase(TomlDuplicatedKeyInspection::class) {

    fun `test top-level properties`() = doTest("""
        <error descr="Property redefinition is not allowed">foo</error> = 1
        <error descr="Property redefinition is not allowed">foo</error> = 2
        bar = 3
    """)

    fun `test category properties`() = doTest("""
        [foo]
        <error descr="Property redefinition is not allowed">foo</error> = 1
        <error descr="Property redefinition is not allowed">foo</error> = 2
        bar = 3
    """)

    fun `test segments`() = doTest("""
        <error descr="Property redefinition is not allowed">foo.bar</error> = 1
        <error descr="Property redefinition is not allowed">"foo" . 'bar'</error> = 2
        foobar = 3
    """)

    fun `test valid segments`() = doTest("""
        foo."  ".bar = 1
        foo." ".bar = 2
        foo."".bar = 3
        foo.bar = 4
    """)

    fun `test inline array valid`() = doTest("""
        foo = [
            { bar = 1 },
            { bar = 2 },
        ]
        bar = 1
    """)

    fun `test no errors for arrays`() = doTest("""
        [foo1]
        bar = 1

        [foo2]
        bar = 2

        [[foo]]
        bar = 1

        [[foo]]
        bar = 2
    """)

    fun `test duplicated key in array`() = doTest("""
        [[foo]]
        <error descr="Property redefinition is not allowed">bar</error> = 1
        <error descr="Property redefinition is not allowed">bar</error> = 1
        baz = 2

        [[foo]]
        bar = 2
    """)

    fun `test duplicated key in inline array`() = doTest("""
        foo = {
            <error descr="Property redefinition is not allowed">bar</error> = 1,
            <error descr="Property redefinition is not allowed">bar</error> = 2,
            baz = 3
        }

        [a]
        bar = [
            {
                <error descr="Property redefinition is not allowed">bar</error> = 1,
                <error descr="Property redefinition is not allowed">bar</error> = 2,
                baz = 3
            },
            {
                <error descr="Property redefinition is not allowed">bar</error> = 1,
                <error descr="Property redefinition is not allowed">bar</error> = 2,
                baz = 3
            }
        ]
    """)
}
