/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key

// BACKCOMPAT 2023.2: move to the RsAnsiEscapeDecoderTest companion
val Key<*>.escapeSequence: String
    get() = (this as? ProcessOutputType)?.escapeSequence ?: toString()
