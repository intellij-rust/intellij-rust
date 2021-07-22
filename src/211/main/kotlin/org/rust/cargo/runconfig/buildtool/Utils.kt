/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:JvmName("Utils211")

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildContentDescriptor
import com.intellij.util.ThreeState

@Suppress("unused")
var BuildContentDescriptor.isNavigateToErrorWhenFailed: ThreeState
    get() = ThreeState.UNSURE
    set(_) {}
