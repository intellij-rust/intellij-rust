/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import kotlin.reflect.KClass

abstract class RsAnnotatorTestBase(private val annotatorClass: KClass<out AnnotatorBase>) : RsAnnotationTestBase() {

    override fun createAnnotationFixture(): RsAnnotationTestFixture =
        RsAnnotationTestFixture(this, myFixture, annotatorClasses = listOf(annotatorClass))
}
