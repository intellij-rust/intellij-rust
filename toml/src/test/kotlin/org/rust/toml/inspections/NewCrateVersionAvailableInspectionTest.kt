/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.CargoRegistryCrateVersion

class NewCrateVersionAvailableInspectionTest : CargoTomlCrateInspectionTestBase(NewCrateVersionAvailableInspection::class) {
    fun `test do not warn about missing crate`() = doTest("""
        [dependencies]
        foo = "1"
    """)

    fun `test do not warn about invalid version`() = doTest("""
        [dependencies]
        foo = "=2.3.4"
    """,
        "foo1" to CargoRegistryCrate.of("1.2.3"),
    )

    fun `test newer version available`() = doTest("""
        [dependencies]
        foo1 = <weak_warning descr="A newer version is available for crate foo1: 2.1.0">"1.2.3"</weak_warning>
    """,
        "foo1" to CargoRegistryCrate.of("1.1.0", "1.2.3", "2.1.0"),
    )

    fun `test newer version available with major version 0`() = doTest("""
        [dependencies]
        foo1 = <weak_warning descr="A newer version is available for crate foo1: 0.2.0">"0.1.1"</weak_warning>
    """,
        "foo1" to CargoRegistryCrate.of("0.1.1", "0.2.0"),
    )

    fun `test major version required`() = doTest("""
        [dependencies]
        foo1 = "2"
    """,
        "foo1" to CargoRegistryCrate.of("2.0.0", "2.1.0", "2.2.0"),
    )

    fun `test ignore yanked higher version`() = doTest("""
        [dependencies]
        foo1 = "1.2"
    """,
        "foo1" to CargoRegistryCrate(listOf(
            CargoRegistryCrateVersion("1.2.0", false, emptyList()),
            CargoRegistryCrateVersion("2.0.0", true, emptyList())
        )),
    )

    fun `test ignore yanked same version`() = doTest("""
        [dependencies]
        foo1 = "2.0.0"
    """,
        "foo1" to CargoRegistryCrate(listOf(
            CargoRegistryCrateVersion("1.2.0", false, emptyList()),
            CargoRegistryCrateVersion("2.0.0", true, emptyList())
        )),
    )

    fun `test ignore pinned version`() = doTest("""
        [dependencies]
        foo1 = "=1.2"
    """,
        "foo1" to CargoRegistryCrate.of("1.2.0", "2.0.0"),
    )

    fun `test ignore unstable version`() = doTest("""
        [dependencies]
        foo1 = "1.2"
    """,
        "foo1" to CargoRegistryCrate.of("1.2.0", "2.0.0-beta3"),
    )

    fun `test unstable version required`() = doTest("""
        [dependencies]
        foo1 = "2.0.0-beta3"
    """,
        "foo1" to CargoRegistryCrate.of("1.2.0", "2.0.0-beta3"),
    )

    fun `test fix update to new version inline`() = checkFix("Update version to 2.0.0", """
        [dependencies]
        foo1 = <weak_warning descr="A newer version is available for crate foo1: 2.0.0">"1.2<caret>"</weak_warning>
    """, """
        [dependencies]
        foo1 = "2.0.0"
    """,
        "foo1" to CargoRegistryCrate.of("1.2.0", "2.0.0"),
    )

    fun `test fix update to new version expanded`() = checkFix("Update version to 2.0.0", """
        [dependencies]
        foo1 = { version = <weak_warning descr="A newer version is available for crate foo1: 2.0.0">"1.2<caret>"</weak_warning> }
    """, """
        [dependencies]
        foo1 = { version = "2.0.0" }
    """,
        "foo1" to CargoRegistryCrate.of("1.2.0", "2.0.0"),
    )
}
