/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import kotlin.reflect.KClass

abstract class RsWithToolchainAnnotatorTestBase<C>(
    private val annotatorClass: KClass<out AnnotatorBase>
) : RsWithToolchainAnnotationTestBase<C>() {

    override fun createAnnotationFixture(): RsAnnotationTestFixture<C> =
        RsAnnotationTestFixture(this, myFixture, annotatorClasses = listOf(annotatorClass))
}
