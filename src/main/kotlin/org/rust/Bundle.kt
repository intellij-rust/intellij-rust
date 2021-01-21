/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val RS_BUNDLE = "messages.RsBundle"

object RsBundle : DynamicBundle(RS_BUNDLE) {
    @Nls
    fun message(@PropertyKey(resourceBundle = RS_BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)
}

private const val CARGO_BUNDLE = "messages.CargoBundle"

object CargoBundle : DynamicBundle(CARGO_BUNDLE) {
    @Nls
    fun message(@PropertyKey(resourceBundle = CARGO_BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)
}
