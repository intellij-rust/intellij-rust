/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsVisitor

/**
 * Call [action] on every "binding" in a pattern, e.g., on `a` in
 * `match foo() { Some(a) => (), None => () }`
 */
fun RsPat.forEachBinding(action: (RsPatBinding) -> Unit) =
    accept(object : RsVisitor() {
        override fun visitPatBinding(binding: RsPatBinding) = action(binding)
        override fun visitElement(element: RsElement) = element.acceptChildren(this)
    })
