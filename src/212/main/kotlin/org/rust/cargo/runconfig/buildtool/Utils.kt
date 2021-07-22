/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:JvmName("Utils212")

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildContentDescriptor
import com.intellij.util.ThreeState

var BuildContentDescriptor.isNavigateToErrorWhenFailed: ThreeState
    get() = isNavigateToError
    set(value) {
        isNavigateToError = value
    }
