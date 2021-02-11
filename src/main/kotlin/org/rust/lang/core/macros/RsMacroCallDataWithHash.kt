/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.stdext.HashCode

class RsMacroCallDataWithHash(val data: RsMacroCallData, val bodyHash: HashCode?)
