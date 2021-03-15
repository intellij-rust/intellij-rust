/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import org.rust.toml.crates.local.CargoRegistryCrate

class CrateNotFoundInspectionTest : CargoTomlCrateInspectionTestBase(CrateNotFoundInspection::class) {
    fun `test missing crate in dependencies`() = doTest("""
        [dependencies]
        <warning descr="Crate foo not found">foo</warning> = "1"
    """)

    fun `test missing crate in dev-dependencies`() = doTest("""
        [dev-dependencies]
        <warning descr="Crate foo not found">foo</warning> = "1"
    """)

    fun `test missing crate complex dependencies`() = doTest("""
        [x86.dependencies]
        <warning descr="Crate foo not found">foo</warning> = "1"
    """)

    fun `test missing crate in specific dependency`() = doTest("""
        [dependencies.<warning descr="Crate foo not found">foo</warning>]
    """)

    fun `test existing crate in dependencies`() = doTest("""
        [dependencies]
        foo = "1"
    """, "foo" to CargoRegistryCrate.of("1"))

    fun `test multiple missing crates`() = doTest("""
        [dependencies]
        <warning descr="Crate foo1 not found">foo1</warning> = "1"
        foo = "1"
        <warning descr="Crate foo2 not found">foo2</warning> = "2"
        bar = "1"
        <warning descr="Crate bar1 not found">bar1</warning> = "1"
    """,
        "foo" to CargoRegistryCrate.of("1"),
        "bar" to CargoRegistryCrate.of("1")
    )

    fun `test dependency inline table`() = doTest("""
        [dependencies]
        <warning descr="Crate foo1 not found">foo1</warning> = { version = "1" }
        foo = { version = "1" }
    """, "foo" to CargoRegistryCrate.of("1"))

    fun `test do not check dependencies with local, custom, git property`() = doTest("""
        [dependencies]
        foo1 = { version = "1", git = "https://foo.bar" }
        foo2 = { version = "1", path = "../foo/bar" }
        foo3 = { version = "1", registry = "foo" }
    """)

    fun `test do not check specific dependency with local, custom, git property`() = doTest("""
        [dependencies.foo1]
        version = "1"
        git = "https://foo.bar"

        [dependencies.foo2]
        version = "1"
        path = "../foo/bar"

        [dependencies.foo3]
        version = "1"
        registry = "foo"
    """)

    fun `test renamed package inline`() = doTest("""
        [dependencies]
        foo1 = { version = "1", package = "foo" }
        foo2 = { version = "1", package = <warning descr="Crate bar not found">"bar"</warning> }
    """,
        "foo" to CargoRegistryCrate.of("1"),
    )

    fun `test renamed package table`() = doTest("""
        [dependencies.foo1]
        package = "foo"

        [dependencies.foo2]
        package = <warning descr="Crate bar not found">"bar"</warning>
    """,
        "foo" to CargoRegistryCrate.of("1"),
    )
}
