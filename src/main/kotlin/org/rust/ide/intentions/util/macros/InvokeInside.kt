/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.util.macros

/**
 * See [org.rust.ide.intentions.RsElementBaseIntentionAction.attributeMacroHandlingStrategy] and
 * [org.rust.ide.intentions.RsElementBaseIntentionAction.functionLikeMacroHandlingStrategy]
 */
enum class InvokeInside {
    MACRO_CALL,
    MACRO_EXPANSION
}
