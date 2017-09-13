/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey

inline fun <Key, reified Psi : PsiElement> getElements(indexKey: StubIndexKey<Key, Psi>,
                                                       key: Key, project: Project,
                                                       scope: GlobalSearchScope?): Collection<Psi> =
    StubIndex.getElements(indexKey, key, project, scope, Psi::class.java)

