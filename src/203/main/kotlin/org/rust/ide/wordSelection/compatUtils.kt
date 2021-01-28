/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.openapi.editor.LineExtensionInfo
import java.util.function.IntFunction

typealias LineExtensionPainter = IntFunction<MutableCollection<LineExtensionInfo>>?
