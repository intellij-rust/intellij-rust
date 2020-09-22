/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiDocCommentBase
import java.util.function.Consumer

// BACKCOMPAT: 2020.2. Inline it
typealias DocCommentConsumer = Consumer<in PsiDocCommentBase>
