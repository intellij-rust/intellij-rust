/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates

import com.intellij.util.text.SemVer

fun parseSemver(version: String): SemVer? = SemVer.parseFromText(version)
