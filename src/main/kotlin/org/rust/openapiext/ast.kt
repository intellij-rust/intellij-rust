/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.lang.ASTNode

/** Iterates all children of [this] node and invokes [action] for each one */
inline fun ASTNode.forEachChild(action: (ASTNode) -> Unit) {
    var treeChild: ASTNode? = firstChildNode

    while (treeChild != null) {
        action(treeChild)
        treeChild = treeChild.treeNext
    }
}
