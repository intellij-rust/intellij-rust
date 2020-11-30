/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import org.rust.lang.core.psi.RsTokenType

/** Used for lexer-based highlighting of documentation comments. These elements are never used in the PSI */
class RsDocHighlightingTokenType(debugName: String) : RsTokenType(debugName)
