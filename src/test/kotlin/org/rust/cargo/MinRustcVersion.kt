/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

/**
 * Specify minimum rustc version to launch test.
 * Handled by [RsWithToolchainTestBase]
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MinRustcVersion(val version: String)
