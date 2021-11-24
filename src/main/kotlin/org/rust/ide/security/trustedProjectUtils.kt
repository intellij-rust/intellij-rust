/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.security

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

private val LOG = Logger.getInstance(":org.rust.ide.security.TrustedProjectUtils")

// Starting with 2021.3.1 and 2021.2.4 some project trusted API was changed,
// so let's check if old API is available not to produce runtime errors
//
// BACKCOMPAT: 2021.3
val isNewTrustedProjectApiAvailable: Boolean get() = whenProjectTrustedFunction != null

fun whenProjectTrusted(parentDisposable: Disposable, listener: (Project) -> Unit) {
    if (whenProjectTrustedFunction == null) {
        LOG.warn("`com.intellij.ide.impl.whenProjectTrusted` is not available")
    }
    whenProjectTrustedFunction?.invoke(parentDisposable, listener)
}

// Reflection based wrapper over `com.intellij.ide.impl.whenProjectTrusted` function
private val whenProjectTrustedFunction: ((Disposable, (Project) -> Unit) -> Unit)? by lazy {
    try {
        val trustedProjectClass = Class.forName("com.intellij.ide.impl.TrustedProjects")
        val method = trustedProjectClass.getMethod("whenProjectTrusted", Disposable::class.java, Function1::class.java);
        { parentDisposable, listener ->
            try {
                method.invoke(null, parentDisposable, listener)
            } catch (e: Throwable) {
                LOG.error(e)
            }
        }
    } catch (ignore: Throwable) {
        null
    }
}
