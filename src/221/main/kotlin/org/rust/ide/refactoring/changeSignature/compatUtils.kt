/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.changeSignature

import com.intellij.util.Consumer
import org.rust.lang.core.psi.RsFunction

typealias CallerChooserCallback = Consumer<Set<RsFunction>>
