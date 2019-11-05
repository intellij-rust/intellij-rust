/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase

abstract class RsAnnotatorTestBase(private val annotatorClass: Class<out AnnotatorBase>) : RsAnnotationTestBase() {
    override fun setUp() {
        super.setUp()
        AnnotatorBase.enableAnnotator(annotatorClass, testRootDisposable)
    }
}
