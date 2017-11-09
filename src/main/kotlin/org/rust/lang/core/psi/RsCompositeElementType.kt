/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.tree.IElementType
import org.rust.lang.RsLanguage

class RsElementType(s: String) : IElementType(s, RsLanguage)
