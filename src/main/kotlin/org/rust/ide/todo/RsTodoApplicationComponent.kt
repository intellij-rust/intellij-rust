/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
// BACKCOMPAT 2019.3. Use `com.intellij.indexPatternSearch` EP to register RsTodoSearcher instead of application component
@file:Suppress("DEPRECATION")

package org.rust.ide.todo

import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.search.searches.IndexPatternSearch

class RsTodoApplicationComponent : BaseComponent {

    override fun initComponent() {
        ServiceManager.getService(IndexPatternSearch::class.java).registerExecutor(RsTodoSearcher())
    }
}
