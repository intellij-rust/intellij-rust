/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.security

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

val isNewTrustedProjectApiAvailable: Boolean get() = true

fun whenProjectTrusted(parentDisposable: Disposable, listener: (Project) -> Unit) {
    @Suppress("UnstableApiUsage")
    com.intellij.ide.impl.whenProjectTrusted(parentDisposable, listener)
}
