/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import java.lang.annotation.Inherited

@Inherited
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithDumbMode(val mode: SmartDumbMode = SmartDumbMode.DUMB) {
    enum class SmartDumbMode { SMART, DUMB }
}
