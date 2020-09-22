/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiDocCommentBase
import java.util.function.Consumer

typealias DocCommentConsumer = Consumer<PsiDocCommentBase>
