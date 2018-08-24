/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import org.rust.cargo.project.model.CargoProject

/**
 * Allows to set certain version of rustc for all [CargoProject]s in test case.
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
