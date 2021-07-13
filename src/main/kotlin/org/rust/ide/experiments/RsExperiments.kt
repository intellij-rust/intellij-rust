/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.experiments

object RsExperiments {
    @EnabledInStable
    const val BUILD_TOOL_WINDOW = "org.rust.cargo.build.tool.window"

    @EnabledInStable
    const val TEST_TOOL_WINDOW = "org.rust.cargo.test.tool.window"

    const val EVALUATE_BUILD_SCRIPTS = "org.rust.cargo.evaluate.build.scripts"

    const val CARGO_FEATURES_SETTINGS_GUTTER = "org.rust.cargo.features.settings.gutter"

    @EnabledInStable
    const val MACROS_NEW_ENGINE = "org.rust.macros.new.engine"

    const val PROC_MACROS = "org.rust.macros.proc"

    @EnabledInStable
    const val RESOLVE_NEW_ENGINE = "org.rust.resolve.new.engine"

    const val FETCH_ACTUAL_STDLIB_METADATA = "org.rust.cargo.fetch.actual.stdlib.metadata"

    const val CRATES_LOCAL_INDEX = "org.rust.crates.local.index"

    const val WSL_TOOLCHAIN = "org.rust.wsl"
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
