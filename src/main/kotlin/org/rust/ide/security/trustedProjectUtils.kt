/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.security

import com.intellij.ide.impl.OpenUntrustedProjectChoice

// Starting with 2021.3.1 and 2021.2.4 some project trusted API was changed,
// so let's check if old API is available not to produce runtime errors
//
// BACKCOMPAT: 2021.3
val isOldTrustedProjectApiAvailable: Boolean by lazy {
    try {
        @Suppress("UnstableApiUsage")
        OpenUntrustedProjectChoice.IMPORT.name
        true
    } catch (e: NoSuchFieldError) {
        false
    }
}
