/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import java.io.IOException

class ProcessCreationException(cause: IOException) : RuntimeException(cause)
