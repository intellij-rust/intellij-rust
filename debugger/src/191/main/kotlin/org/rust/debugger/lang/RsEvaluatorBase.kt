/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.cidr.execution.debugger.OCEvaluator

abstract class RsEvaluatorBase(frame: CidrStackFrame) : OCEvaluator(frame)
