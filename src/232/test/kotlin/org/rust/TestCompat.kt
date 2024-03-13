/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.util.Key

val Key<*>.escapeSequence: String
    get() = toString()
