/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler.perf

import com.intellij.profiler.clion.perf.PerfProfilerSettings
import com.intellij.profiler.dtrace.SimpleProfilerSettingsState

fun getPerfSettings(): SimpleProfilerSettingsState = PerfProfilerSettings.instance.state
