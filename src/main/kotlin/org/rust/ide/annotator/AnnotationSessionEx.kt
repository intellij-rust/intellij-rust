/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.util.Key
import com.intellij.util.containers.orNull
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.getOrPut
import java.util.*

private val CRATE_KEY = Key<Optional<Crate>>("org.rust.ide.annotator.currentCrate")

fun AnnotationSession.currentCrate(): Crate? {
    return getOrPut(CRATE_KEY) {
        Optional.ofNullable((file as? RsFile)?.crate)
    }.orNull()
}

fun AnnotationSession.setCurrentCrate(crate: Crate?) {
    putUserData(CRATE_KEY, Optional.ofNullable(crate))
}

fun AnnotationHolder.currentCrate(): Crate? = currentAnnotationSession.currentCrate()
