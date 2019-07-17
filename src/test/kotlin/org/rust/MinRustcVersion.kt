/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.util.text.SemVer

/**
 * Specify minimum rustc version to launch test.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class MinRustcVersion(val version: String)

val MinRustcVersion.semver: SemVer get() = SemVer.parseFromText(version) ?: error("Invalid version value: $version")
