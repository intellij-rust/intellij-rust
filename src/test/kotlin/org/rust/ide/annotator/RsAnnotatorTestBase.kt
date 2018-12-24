/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

abstract class RsAnnotatorTestBase(private val annotatorClass: Class<out RsAnnotatorBase>) : RsAnnotationTestBase() {
    override fun setUp() {
        super.setUp()
        RsAnnotatorBase.enableAnnotator(annotatorClass, testRootDisposable)
    }
}
