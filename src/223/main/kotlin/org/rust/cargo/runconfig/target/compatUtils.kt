/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.TargetedCommandLineBuilder
import java.nio.charset.Charset

// BACKCOMPAT: 2022.2. Inline it
fun TargetedCommandLineBuilder.setCharset(charset: Charset) {
    this.charset = charset
}
