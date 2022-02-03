/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import junit.framework.TestCase
import org.rust.lang.core.resolve2.defMapService

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UseOldResolve

fun TestCase.setupResolveEngine(project: Project, testRootDisposable: Disposable) {
    val defMap = project.defMapService
    findAnnotationInstance<UseOldResolve>()?.let {
        defMap.setNewResolveEnabled(testRootDisposable, false)
    }
}
