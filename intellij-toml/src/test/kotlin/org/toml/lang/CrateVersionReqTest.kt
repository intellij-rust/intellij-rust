/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

import org.junit.Test

// Based on https://github.com/steveklabnik/semver/blob/master/src/version_req.rs
class CrateVersionReqTest {
    private fun assertMatches(req: CrateVersionReq, vararg matchingVersions: String) {
        for (version in matchingVersions) {
            assert(req.matches(CrateVersion.parse(version))) { "Crate version requirements $req should have matched version $version" }
        }
    }

    private fun assertNotMatches(req: CrateVersionReq, vararg matchingVersions: String) {
        for (version in matchingVersions) {
            assert(!req.matches(CrateVersion.parse(version))) { "Crate version requirements $req shouldn't have matched version $version" }
        }
    }

    @Test
    fun `test parsing default`() {
        val req = CrateVersionReq.parse("1.0.0")

        assertMatches(req, "1.0.0", "1.0.1")
        assertNotMatches(req, "0.9.9", "0.10.0", "0.1.0")
    }

    @Test
    fun `test parsing exact #1`() {
        val req = CrateVersionReq.parse("=1.0.0")

        assertMatches(req, "1.0.0")
        assertNotMatches(req, "1.0.1", "0.9.9", "0.10.0", "0.1.0")
    }

    @Test
    fun `test parsing exact #2`() {
        val req = CrateVersionReq.parse("=0.9.0")

        assertMatches(req, "0.9.0")
        assertNotMatches(req, "1.0.1", "0.9.9", "0.10.0", "0.1.0")
    }

    @Test
    fun `test parsing greater than #1`() {
        val req = CrateVersionReq.parse(">=1.0.0")

        assertMatches(req, "1.0.0", "2.0.0")
        assertNotMatches(req, "0.1.0", "0.0.1", "1.0.0-pre", "2.0.0-pre")
    }

    @Test
    fun `test parsing greater than #2`() {
        val req = CrateVersionReq.parse(">= 2.1.0-alpha2")

        assertMatches(req, "2.1.0-alpha2", "2.1.0-alpha3", "2.1.0", "3.1.0")
        assertNotMatches(req, "2.0.0", "2.1.0-alpha1", "3.0.0-alpha2")
    }

    @Test
    fun `test parsing less than #1`() {
        val req = CrateVersionReq.parse("< 1.0.0")

        assertMatches(req, "0.1.0", "0.0.1")
        assertNotMatches(req, "1.0.0", "1.0.0-beta", "1.0.1", "0.9.9-alpha")
    }

    @Test
    fun `test parsing less than #2`() {
        val req = CrateVersionReq.parse("<= 2.1.0-alpha2")

        assertMatches(req, "2.1.0-alpha2", "2.1.0-alpha1", "2.0.0", "1.0.0")
        assertNotMatches(req, "2.1.0", "2.2.0-alpha1", "2.0.0-alpha2", "1.0.0-alpha2")
    }

    @Test
    fun `test parsing multiple #1`() {
        val req = CrateVersionReq.parse("> 0.0.9, <= 2.5.3")

        assertMatches(req, "0.0.10", "1.0.0", "2.5.3")
        assertNotMatches(req, "0.0.8", "2.5.4")
    }

    @Test
    fun `test parsing multiple #2`() {
        val req = CrateVersionReq.parse("0.3.0, 0.4.0")

        assertNotMatches(req, "0.0.8", "0.3.0", "0.4.0")
    }

    @Test
    fun `test parsing multiple #3`() {
        val req = CrateVersionReq.parse("<= 0.2.0, >= 0.5.0")

        assertNotMatches(req, "0.0.8", "0.3.0", "0.5.1")
    }

    @Test
    fun `test parsing multiple #4`() {
        val req = CrateVersionReq.parse("0.1.0, 0.1.4, 0.1.6")

        assertMatches(req, "0.1.6", "0.1.9")
        assertNotMatches(req, "0.1.0", "0.1.4", "0.2.0")
    }

    @Test
    fun `test parsing multiple #5`() {
        val req = CrateVersionReq.parse(">=0.5.1-alpha3, <0.6")

        assertMatches(req, "0.5.1-alpha3", "0.5.1-alpha4", "0.5.1-beta", "0.5.1", "0.5.5")
        assertNotMatches(req, "0.5.1-alpha1", "0.5.2-alpha2", "0.5.5-pre", "0.5.0-pre")
    }

    @Test
    fun `test parsing tilde #1`() {
        val req = CrateVersionReq.parse("~1")

        assertMatches(req, "1.0.0", "1.0.1", "1.1.1")
        assertNotMatches(req, "0.9.1", "2.9.0", "0.0.9")
    }

    @Test
    fun `test parsing tilde #2`() {
        val req = CrateVersionReq.parse("~1.2")

        assertMatches(req, "1.2.0", "1.2.1")
        assertNotMatches(req, "1.1.1", "1.3.0", "0.0.9")
    }

    @Test
    fun `test parsing tilde #3`() {
        val req = CrateVersionReq.parse("~1.2.2")

        assertMatches(req, "1.2.2", "1.2.4")
        assertNotMatches(req, "1.2.1", "1.9.0", "1.0.9", "2.0.1", "0.1.3")
    }

    @Test
    fun `test parsing tilde #4`() {
        val req = CrateVersionReq.parse("~1.2.3-beta.2")

        assertMatches(req, "1.2.3", "1.2.3-beta.2", "1.2.3-beta.4", "1.2.4")
        assertNotMatches(req, "1.3.3", "1.1.4", "1.2.3-beta.1", "1.2.4-beta.2")
    }

    @Test
    fun `test parsing compatible #1`() {
        val req = CrateVersionReq.parse("^1")

        assertMatches(req, "1.1.2", "1.1.0", "1.2.1", "1.0.1")
        assertNotMatches(req, "0.9.1", "2.9.0", "0.1.4", "1.0.0-beta1", "0.1.0-alpha", "1.0.1-pre")
    }

    @Test
    fun `test parsing compatible #2`() {
        val req = CrateVersionReq.parse("^1.1")

        assertMatches(req, "1.1.2", "1.1.0", "1.2.1")
        assertNotMatches(req, "1.0.1", "0.9.1", "2.9.0", "0.1.4", "1.0.0-beta1", "0.1.0-alpha", "1.0.1-pre")
    }

    @Test
    fun `test parsing compatible #3`() {
        val req = CrateVersionReq.parse("^1.1.2")

        assertMatches(req, "1.1.2", "1.1.4", "1.2.1")
        assertNotMatches(req, "0.9.1", "2.9.0", "1.1.1", "0.0.1", "1.1.2-alpha1", "1.1.3-alpha1", "2.9.0-alpha1")
    }

    @Test
    fun `test parsing compatible #4`() {
        val req = CrateVersionReq.parse("^0.1.2")

        assertMatches(req, "0.1.2", "0.1.4")
        assertNotMatches(req, "0.9.1", "2.9.0", "1.1.1", "0.0.1", "0.1.2-beta", "0.1.3-alpha", "0.2.0-pre")
    }

    @Test
    fun `test parsing compatible #5`() {
        val req = CrateVersionReq.parse("^0.5.1-alpha3")

        assertMatches(req, "0.5.1-alpha3", "0.5.1-alpha4", "0.5.1-beta", "0.5.1", "0.5.5")
        assertNotMatches(req, "0.5.1-alpha1", "0.5.2-alpha3", "0.5.5-pre", "0.5.0-pre", "0.6.0")
    }

    @Test
    fun `test parsing compatible #6`() {
        val req = CrateVersionReq.parse("^0.0.2")

        assertMatches(req, "0.0.2")
        assertNotMatches(req, "0.9.1", "2.9.0", "1.1.1", "0.0.1", "0.1.4")
    }

    @Test
    fun `test parsing compatible #7`() {
        val req = CrateVersionReq.parse("^0.0")

        assertMatches(req, "0.0.2", "0.0.0")
        assertNotMatches(req, "0.9.1", "2.9.0", "1.1.1", "0.1.4")
    }

    @Test
    fun `test parsing compatible #8`() {
        val req = CrateVersionReq.parse("^0")

        assertMatches(req, "0.9.1", "0.0.2", "0.0.0")
        assertNotMatches(req, "2.9.0", "1.1.1")
    }

    @Test
    fun `test parsing compatible #9`() {
        val req = CrateVersionReq.parse("^1.4.2-beta.5")

        assertMatches(req, "1.4.2", "1.4.3", "1.4.2-beta.5", "1.4.2-beta.6", "1.4.2-c")
        assertNotMatches(req, "0.9.9", "2.0.0", "1.4.2-alpha", "1.4.2-beta.4", "1.4.3-beta.5")
    }

    @Test
    fun `test parsing wildcard #1`() {
        val req = CrateVersionReq.parse("")

        assertMatches(req, "0.9.1", "2.9.0", "0.0.9", "1.0.1", "1.1.1")
        assertNotMatches(req)
    }

    @Test
    fun `test parsing wildcard #2`() {
        val req = CrateVersionReq.parse("*")

        assertMatches(req, "0.9.1", "2.9.0", "0.0.9", "1.0.1", "1.1.1")
        assertNotMatches(req)
    }


    @Test
    fun `test parsing wildcard #3`() {
        val req = CrateVersionReq.parse("1.*")

        assertMatches(req, "1.2.0", "1.2.1", "1.1.1", "1.3.0")
        assertNotMatches(req, "0.0.9")
    }

    @Test
    fun `test parsing wildcard #4`() {
        val req = CrateVersionReq.parse("1.2.*")

        assertMatches(req, "1.2.0", "1.2.2", "1.2.4")
        assertNotMatches(req, "1.9.0", "1.0.9", "2.0.1", "0.1.3")
    }
}
