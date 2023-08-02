/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.test.util

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter

class RsTestJsonPrettyPrinter : DefaultPrettyPrinter() {
    init {
        _objectFieldValueSeparatorWithSpaces = ": "
        _objectIndenter = UNIX_LINE_FEED_INSTANCE
    }

    companion object {
        private val UNIX_LINE_FEED_INSTANCE = DefaultIndenter("  ", "\n")
    }
}
