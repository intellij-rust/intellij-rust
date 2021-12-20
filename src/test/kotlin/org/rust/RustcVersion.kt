/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.util.text.SemVer
import junit.framework.TestCase
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.util.parseSemVer
import org.rust.stdext.RsResult

/**
 * Allows setting certain version of rustc for all [CargoProject]s in test case.
 *
 * [rustcVersion] should match the following pattern: `(\d+\.\d+\.\d+).*`.
 * For example:
 *   * `1.27.0` set stable `1.27.0` rustc version
 *   * `1.28.0-beta.1` set beta `1.28.0` rustc version
 *   * `1.29.0-nightly` set nightly `1.29.0` rustc version
 *
 * Note, only inheritors of [RsTestBase] support this annotation.
 *
 * @see CargoProject.rustcInfo
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MockRustcVersion(val rustcVersion: String)

/**
 * Specify minimum rustc version to launch test.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MinRustcVersion(val version: String)

val MinRustcVersion.semver: SemVer get() = version.parseSemVer()

/**
 * Specify maximum rustc version to launch test.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MaxRustcVersion(val version: String)

val MaxRustcVersion.semver: SemVer get() = version.parseSemVer()

fun TestCase.checkRustcVersionRequirements(rustcVersionSupplier: () -> RsResult<SemVer, String?>): String? {
    val minRustVersion = findAnnotationInstance<MinRustcVersion>()
    val maxRustVersion = findAnnotationInstance<MaxRustcVersion>()
    if (minRustVersion == null && maxRustVersion == null) return null

    val rustcVersion = when (val result = rustcVersionSupplier()) {
        is RsResult.Ok -> result.ok
        is RsResult.Err -> return result.err
    }

    if (minRustVersion != null) {
        val requiredVersion = minRustVersion.semver
        if (rustcVersion < requiredVersion) return "At least $requiredVersion Rust version required, $rustcVersion found"
    }

    if (maxRustVersion != null) {
        val requiredVersion = maxRustVersion.semver
        if (rustcVersion > requiredVersion) return "At most $requiredVersion Rust version required, $rustcVersion found"
    }
    return null
}

