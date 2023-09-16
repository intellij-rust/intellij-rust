/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.experiments

object RsExperiments {
    @EnabledInStable
    const val BUILD_TOOL_WINDOW = "org.rust.cargo.build.tool.window"

    @EnabledInStable
    const val EVALUATE_BUILD_SCRIPTS = "org.rust.cargo.evaluate.build.scripts"

    const val CARGO_FEATURES_SETTINGS_GUTTER = "org.rust.cargo.features.settings.gutter"

    const val PROC_MACROS = "org.rust.macros.proc"
    @EnabledInStable
    const val FN_LIKE_PROC_MACROS = "org.rust.macros.proc.function-like"
    @EnabledInStable
    const val DERIVE_PROC_MACROS = "org.rust.macros.proc.derive"
    const val ATTR_PROC_MACROS = "org.rust.macros.proc.attr"

    @EnabledInStable
    const val FETCH_ACTUAL_STDLIB_METADATA = "org.rust.cargo.fetch.actual.stdlib.metadata"

    @EnabledInStable
    const val CRATES_LOCAL_INDEX = "org.rust.crates.local.index"

    @EnabledInStable
    const val WSL_TOOLCHAIN = "org.rust.wsl"

    const val EMULATE_TERMINAL = "org.rust.cargo.emulate.terminal"

    const val INTENTIONS_IN_FN_LIKE_MACROS = "org.rust.ide.intentions.macros.function-like"

    const val SSR = "org.rust.ssr"

    const val SOURCE_BASED_COVERAGE = "org.rust.coverage.source"

    const val MIR_MOVE_ANALYSIS = "org.rust.mir.move-analysis"
    const val MIR_BORROW_CHECK = "org.rust.mir.borrow-check"
}

/**
 * Experimental feature should be annotated with `@EnabledInStable` if it is enabled in stable releases,
 * i.e. it is included in `resources-stable/META-INF/experiments.xml` with `percentOfUsers="100"`.
 *
 * Enabled experimental features without `@EnabledInStable` annotation are intended to be collected in
 * [org.rust.ide.actions.diagnostic.CreateNewGithubIssue]
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnabledInStable
