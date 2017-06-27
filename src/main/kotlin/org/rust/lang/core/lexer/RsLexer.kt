/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.lexer

import com.intellij.lexer.FlexAdapter

class RsLexer : FlexAdapter(_RustLexer())
