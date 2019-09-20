/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import org.junit.Test
import org.toml.lang.CrateVersion.Companion.parse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Based on https://github.com/steveklabnik/semver/blob/master/src/version.rs
class CrateVersionTest {
    @Test
    fun `test parse`() {
        assertEquals(CrateVersion(1, 2, 3), parse("1.2.3"))
        assertEquals(CrateVersion(1, 2, 3), parse("  1.2.3  "))
        assertEquals(CrateVersion(1, 2, 3, listOf(Identifier.AlphaNumeric("alpha1"))), parse("1.2.3-alpha1"))
        assertEquals(CrateVersion(1, 2, 3, listOf(Identifier.AlphaNumeric("alpha1"))), parse("  1.2.3-alpha1  "))
        assertEquals(CrateVersion(1, 2, 3, emptyList(), listOf(Identifier.AlphaNumeric("build5"))), parse("1.2.3+build5"))
        assertEquals(CrateVersion(1, 2, 3, emptyList(), listOf(Identifier.AlphaNumeric("build5"))), parse("  1.2.3+build5  "))
        assertEquals(CrateVersion(1, 2, 3, listOf(Identifier.Numeric(1), Identifier.AlphaNumeric("alpha1"), Identifier.Numeric(9)), listOf(Identifier.AlphaNumeric("build5"), Identifier.Numeric(7), Identifier.AlphaNumeric("3aedf"))), parse("1.2.3-1.alpha1.9+build5.7.3aedf  "))
        assertEquals(CrateVersion(0, 4, 0, listOf(Identifier.AlphaNumeric("beta"), Identifier.Numeric(1)), listOf(Identifier.AlphaNumeric("0851523"))), parse("0.4.0-beta.1+0851523"))
        assertEquals(CrateVersion(1, 0, 0, emptyList(), listOf(Identifier.Numeric(1))), parse("1.0.0+1"))
    }

    @Test
    fun `test lt`() {
        assertTrue(parse("0.0.0") < parse("1.2.3-alpha2"))
        assertTrue(parse("1.0.0") < parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.0") < parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3-alpha1") < parse("1.2.3"))
        assertTrue(parse("1.2.3-alpha1") < parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3-alpha2") >= parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3+23") < parse("1.2.3+42"))
    }

    @Test
    fun `test le`() {
        assertTrue(parse("0.0.0") <= parse("1.2.3-alpha2"))
        assertTrue(parse("1.0.0") <= parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.0") <= parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3-alpha1") <= parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3-alpha2") <= parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3+23") <= parse("1.2.3+42"))
    }

    @Test
    fun `test gt`() {
        assertTrue(parse("1.2.3-alpha2") > parse("0.0.0"))
        assertTrue(parse("1.2.3-alpha2") > parse("1.0.0"))
        assertTrue(parse("1.2.3-alpha2") > parse("1.2.0"))
        assertTrue(parse("1.2.3-alpha2") > parse("1.2.3-alpha1"))
        assertTrue(parse("1.2.3") > parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3-alpha2") <= parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3+23") <= parse("1.2.3+42"))
    }

    @Test
    fun `test ge`() {
        assertTrue(parse("1.2.3-alpha2") >= parse("0.0.0"))
        assertTrue(parse("1.2.3-alpha2") >= parse("1.0.0"))
        assertTrue(parse("1.2.3-alpha2") >= parse("1.2.0"))
        assertTrue(parse("1.2.3-alpha2") >= parse("1.2.3-alpha1"))
        assertTrue(parse("1.2.3-alpha2") >= parse("1.2.3-alpha2"))
        assertTrue(parse("1.2.3+23") < parse("1.2.3+42"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse empty`() {
        parse("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse empty 2`() {
        parse("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse major only`() {
        parse("1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse without patch`() {
        parse("1.2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse invalid pre`() {
        parse("1.2.3-")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse invalid identifiers`() {
        parse("a.b.c")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test parse with junk after version`() {
        parse("1.2.3 abc")
    }
}
