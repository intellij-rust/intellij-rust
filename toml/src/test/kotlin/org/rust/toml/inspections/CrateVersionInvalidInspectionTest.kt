/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.CargoRegistryCrateVersion

class CrateVersionInvalidInspectionTest : CargoTomlCrateInspectionTestBase(CrateVersionInvalidInspection::class) {
    fun `test do not warn about missing crate`() = doTest("""
        [dependencies]
        foo = "1"
    """)

    fun `test standalone version patch`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching 1.2.3 found for crate foo1">"1.2.3"</warning>
        foo2 = <warning descr="No version matching 1.2.3 found for crate foo2">"1.2.3"</warning>
        foo3 = <warning descr="No version matching 1.2.3 found for crate foo3">"1.2.3"</warning>
        foo4 = "1.2.3"
        foo5 = "1.2.3"
        foo6 = "1.2.3"
        foo7 = <warning descr="No version matching 1.2.3 found for crate foo7">"1.2.3"</warning>
        foo8 = <warning descr="No version matching 0.1.2 found for crate foo8">"0.1.2"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.0.0"),
        "foo2" to CargoRegistryCrate.of("1.1.0"),
        "foo3" to CargoRegistryCrate.of("1.2.2"),
        "foo4" to CargoRegistryCrate.of("1.2.3"),
        "foo5" to CargoRegistryCrate.of("1.2.4"),
        "foo6" to CargoRegistryCrate.of("1.3.0"),
        "foo7" to CargoRegistryCrate.of("2.0.0"),
        "foo8" to CargoRegistryCrate.of("0.2.0"),
    )

    fun `test standalone version minor`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching 1.2 found for crate foo1">"1.2"</warning>
        foo2 = "1.2"
        foo3 = "1.2"
        foo4 = "1.2"
        foo5 = <warning descr="No version matching 1.2 found for crate foo5">"1.2"</warning>
        foo6 = <warning descr="No version matching 0.2 found for crate foo6">"0.2"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.0.0"),
        "foo2" to CargoRegistryCrate.of("1.2.0"),
        "foo3" to CargoRegistryCrate.of("1.2.3"),
        "foo4" to CargoRegistryCrate.of("1.3.0"),
        "foo5" to CargoRegistryCrate.of("2.0.0"),
        "foo6" to CargoRegistryCrate.of("0.3.0"),
    )

    fun `test standalone version major`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching 1 found for crate foo1">"1"</warning>
        foo2 = "1"
        foo3 = "1"
        foo4 = <warning descr="No version matching 1 found for crate foo4">"1"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("0.1.0"),
        "foo2" to CargoRegistryCrate.of("1.0.0"),
        "foo3" to CargoRegistryCrate.of("1.2.3"),
        "foo4" to CargoRegistryCrate.of("2.0.0"),
    )

    fun `test caret version patch`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching ^1.2.3 found for crate foo1">"^1.2.3"</warning>
        foo2 = <warning descr="No version matching ^1.2.3 found for crate foo2">"^1.2.3"</warning>
        foo3 = <warning descr="No version matching ^1.2.3 found for crate foo3">"^1.2.3"</warning>
        foo4 = "^1.2.3"
        foo5 = "^1.2.3"
        foo6 = "^1.2.3"
        foo7 = <warning descr="No version matching ^1.2.3 found for crate foo7">"^1.2.3"</warning>
        foo8 = <warning descr="No version matching ^0.1.2 found for crate foo8">"^0.1.2"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.0.0"),
        "foo2" to CargoRegistryCrate.of("1.1.0"),
        "foo3" to CargoRegistryCrate.of("1.2.2"),
        "foo4" to CargoRegistryCrate.of("1.2.3"),
        "foo5" to CargoRegistryCrate.of("1.2.4"),
        "foo6" to CargoRegistryCrate.of("1.3.0"),
        "foo7" to CargoRegistryCrate.of("2.0.0"),
        "foo8" to CargoRegistryCrate.of("0.2.0"),
    )

    fun `test caret version minor`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching ^1.2 found for crate foo1">"^1.2"</warning>
        foo2 = "^1.2"
        foo3 = "^1.2"
        foo4 = "^1.2"
        foo5 = <warning descr="No version matching ^1.2 found for crate foo5">"^1.2"</warning>
        foo6 = <warning descr="No version matching ^0.2 found for crate foo6">"^0.2"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.0.0"),
        "foo2" to CargoRegistryCrate.of("1.2.0"),
        "foo3" to CargoRegistryCrate.of("1.2.3"),
        "foo4" to CargoRegistryCrate.of("1.3.0"),
        "foo5" to CargoRegistryCrate.of("2.0.0"),
        "foo6" to CargoRegistryCrate.of("0.3.0"),
    )

    fun `test caret version major`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching ^1 found for crate foo1">"^1"</warning>
        foo2 = "^1"
        foo3 = "^1"
        foo4 = <warning descr="No version matching ^1 found for crate foo4">"^1"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("0.1.0"),
        "foo2" to CargoRegistryCrate.of("1.0.0"),
        "foo3" to CargoRegistryCrate.of("1.2.3"),
        "foo4" to CargoRegistryCrate.of("2.0.0"),
    )

    fun `test tilde version patch`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching ~1.2.3 found for crate foo1">"~1.2.3"</warning>
        foo2 = "~1.2.3"
        foo3 = "~1.2.3"
        foo4 = <warning descr="No version matching ~1.2.3 found for crate foo4">"~1.2.3"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.2.2"),
        "foo2" to CargoRegistryCrate.of("1.2.3"),
        "foo3" to CargoRegistryCrate.of("1.2.4"),
        "foo4" to CargoRegistryCrate.of("1.3.0"),
    )

    fun `test tilde version minor`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching ~1.2 found for crate foo1">"~1.2"</warning>
        foo2 = "~1.2"
        foo3 = "~1.2"
        foo4 = <warning descr="No version matching ~1.2 found for crate foo4">"~1.2"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.1.0"),
        "foo2" to CargoRegistryCrate.of("1.2.0"),
        "foo3" to CargoRegistryCrate.of("1.2.3"),
        "foo4" to CargoRegistryCrate.of("1.3.0"),
    )

    fun `test tilde version major`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching ~1 found for crate foo1">"~1"</warning>
        foo2 = "~1"
        foo3 = "~1"
        foo4 = <warning descr="No version matching ~1 found for crate foo4">"~1"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("0.1.0"),
        "foo2" to CargoRegistryCrate.of("1.0.0"),
        "foo3" to CargoRegistryCrate.of("1.2.3"),
        "foo4" to CargoRegistryCrate.of("2.0.0"),
    )

    fun `test wildcard version patch`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching 1.2.* found for crate foo1">"1.2.*"</warning>
        foo2 = "1.2.*"
        foo3 = "1.2.*"
        foo4 = <warning descr="No version matching 1.2.* found for crate foo4">"1.2.*"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.1.0"),
        "foo2" to CargoRegistryCrate.of("1.2.0"),
        "foo3" to CargoRegistryCrate.of("1.2.3"),
        "foo4" to CargoRegistryCrate.of("1.3.0"),
    )

    fun `test wildcard version minor`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching 1.* found for crate foo1">"1.*"</warning>
        foo2 = "1.*"
        foo3 = "1.*"
        foo4 = <warning descr="No version matching 1.* found for crate foo4">"1.*"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("0.1.0"),
        "foo2" to CargoRegistryCrate.of("1.0.0"),
        "foo2" to CargoRegistryCrate.of("1.1.0"),
        "foo4" to CargoRegistryCrate.of("2.0.0"),
    )

    fun `test wildcard version major`() = doTest("""
        [dependencies]
        foo1 = "*"
        foo2 = "*"
        foo3 = "*"
    """,
        "foo1" to CargoRegistryCrate.of("0.1.0"),
        "foo2" to CargoRegistryCrate.of("1.0.0"),
        "foo3" to CargoRegistryCrate.of("2.3.4"),
    )

    fun `test empty version`() = doTest("""
        [dependencies]
        foo1 = ""
        foo2 = ""
        foo3 = ""
    """,
        "foo1" to CargoRegistryCrate.of("0.1.0"),
        "foo2" to CargoRegistryCrate.of("1.0.0"),
        "foo3" to CargoRegistryCrate.of("2.3.4"),
    )

    fun `test exact version patch`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching =1.2.3 found for crate foo1">"=1.2.3"</warning>
        foo2 = "=1.2.3"
        foo3 = <warning descr="No version matching =1.2.3 found for crate foo3">"=1.2.3"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.2.2"),
        "foo2" to CargoRegistryCrate.of("1.2.3"),
        "foo3" to CargoRegistryCrate.of("1.2.4"),
    )

    fun `test search all versions`() = doTest("""
        [dependencies]
        foo1 = "=1.2.3"
    """,
        "foo1" to CargoRegistryCrate.of("0.1.2", "1.2.2", "1.2.3","3.4.5"),
    )

    fun `test version range`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching >=1.2.0 found for crate foo1">">=1.2.0"</warning>
        foo2 = <warning descr="No version matching >=1.2 found for crate foo2">">=1.2"</warning>
        foo3 = ">=1.2"
        foo4 = ">=1.2"
        foo5 = "<2"
        foo6 = <warning descr="No version matching <2 found for crate foo6">"<2"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.1.2"),
        "foo2" to CargoRegistryCrate.of("1.1.2"),
        "foo3" to CargoRegistryCrate.of("1.2.3"),
        "foo4" to CargoRegistryCrate.of("2.3.4"),
        "foo5" to CargoRegistryCrate.of("1.3.4"),
        "foo6" to CargoRegistryCrate.of("2.3.4"),
    )

    fun `test multiple version requirements`() = doTest("""
        [dependencies]
        foo1 = <warning descr="No version matching >=1.2.0, <1.4.3 found for crate foo1">">=1.2.0, <1.4.3"</warning>
        foo2 = ">=1.2.0, <1.4.3"
        foo3 = ">=1.2.0, <1.4.3"
        foo4 = ">=1.2.0, <1.4.3"
        foo5 = <warning descr="No version matching >=1.2.0, <1.4.3 found for crate foo5">">=1.2.0, <1.4.3"</warning>
        foo6 = <warning descr="No version matching >=1.2.0, <1.4.3 found for crate foo6">">=1.2.0, <1.4.3"</warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.1.2"),
        "foo2" to CargoRegistryCrate.of("1.2.0"),
        "foo3" to CargoRegistryCrate.of("1.3.5"),
        "foo4" to CargoRegistryCrate.of("1.4.2"),
        "foo5" to CargoRegistryCrate.of("1.4.3"),
        "foo6" to CargoRegistryCrate.of("1.5"),
    )

    fun `test matching versions not yanked`() = doTest("""
        [dependencies]
        foo1 = ">=1.2.0"
    """,
        "foo1" to CargoRegistryCrate(listOf(
            CargoRegistryCrateVersion("1.2.2", true, listOf()),
            CargoRegistryCrateVersion("1.2.3", true, listOf()),
            CargoRegistryCrateVersion("1.3.0", false, listOf())
        ))
    )

    fun `test matching versions yanked`() = doTest("""
        [dependencies]
        foo1 = <warning descr="All versions matching >=1.2.0 for crate foo1 are yanked">">=1.2.0"</warning>
    """,
        "foo1" to CargoRegistryCrate(listOf(
            CargoRegistryCrateVersion("1.1.0", false, listOf()),
            CargoRegistryCrateVersion("1.2.2", true, listOf()),
            CargoRegistryCrateVersion("1.2.3", true, listOf()),
            CargoRegistryCrateVersion("1.3.0", true, listOf())
        ))
    )

    fun `test dependency inline table`() = doTest("""
        [dependencies]
        foo = { version = <warning descr="No version matching =1.2.0 found for crate foo">"=1.2.0"</warning> }
    """,
        "foo" to CargoRegistryCrate.of("1.1.0")
    )

    fun `test dependency specific table`() = doTest("""
        [dependencies.foo]
        version = <warning descr="No version matching =1.2.0 found for crate foo">"=1.2.0"</warning>
    """,
        "foo" to CargoRegistryCrate.of("1.1.0")
    )

    fun `test renamed package`() = doTest("""
        [dependencies]
        foo1 = { version = "=1.2.0", package = "bar" }
        foo2 = { version = <warning descr="No version matching =1.2.0 found for crate baz">"=1.2.0"</warning>, package = "baz" }
    """,
        "bar" to CargoRegistryCrate.of("1.2.0"),
        "baz" to CargoRegistryCrate.of("1.3.0")
    )

    fun `test invalid version`() = doTest("""
        [dependencies]
        foo1 = <weak_warning descr="Invalid version requirement 0.4.0-rc--3-42=34=-23=-42=">"0.4.0-rc--3-42=34=-23=-42="</weak_warning>
        foo2 = <weak_warning descr="Invalid version requirement ---### aqwe58768">"---### aqwe58768"</weak_warning>
        foo3 = <weak_warning descr="Invalid version requirement 1.2.||?8">"1.2.||?8"</weak_warning>
        foo4 = <weak_warning descr="Invalid version requirement *,">"*,"</weak_warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.0.0"),
        "foo2" to CargoRegistryCrate.of("1.0.0"),
        "foo3" to CargoRegistryCrate.of("1.0.0"),
        "foo4" to CargoRegistryCrate.of("1.0.0"),
    )

    fun `test valid version with numbers in pre-release & build sections`() = doTest("""
        foo = { version = "0.4.0-2" }
        bar = { version = "0.4.0-2.1" }
        baz = { version = "0.4.0-a2.1f" }
        qux = { version = "0.4.0-a21f+1" }
        uno = { version = "0.4.0-a2.1f+1" }
        dos = { version = "^0.4.0-a2.1f+1" }
        tre = { version = "~0.4.0-a2.1f+1" }
        qua = { version = "=0.4.0-a2.1f+1" }
        cin = { version = ">0.4.0-a2.1f+1, <0.4.0-a2.1f+5" }
    """,
        "foo" to CargoRegistryCrate.of("0.4.0-2"),
        "bar" to CargoRegistryCrate.of("0.4.0-2.1"),
        "baz" to CargoRegistryCrate.of("0.4.0-a2.1f"),
        "qux" to CargoRegistryCrate.of("0.4.0-a21f+1"),
        "uno" to CargoRegistryCrate.of("0.4.0-a2.1f+1"),
        "dos" to CargoRegistryCrate.of("0.4.0-a2.1f+1"),
        "tre" to CargoRegistryCrate.of("0.4.0-a2.1f+1"),
        "qua" to CargoRegistryCrate.of("0.4.0-a2.1f+1"),
        "cin" to CargoRegistryCrate.of("0.4.0-a2.1f+2")
    )
}
