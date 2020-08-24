/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates

import com.intellij.openapi.project.Project
import org.rust.toml.crates.impl.TestCrateResolverImpl

val Project.testCrateResolver: TestCrateResolverImpl get() = crateResolver as TestCrateResolverImpl
