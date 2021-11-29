/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.openapiext.isUnitTestMode


/**
 * Specifies pre_defined (hardcoded) behavior of some known procedural macros.
 *
 * maps `package name` to a mapping `macro name` => [KnownProcMacroKind]
 */
private val RS_HARDCODED_PROC_MACRO_ATTRIBUTES: Map<String, Map<String, KnownProcMacroKind>> = mapOf(
    "tokio_macros" to mapOf(
        "main" to KnownProcMacroKind.ASYNC_MAIN,
        "test" to KnownProcMacroKind.ASYNC_TEST,
    ),
    "async_attributes" to mapOf(
        "main" to KnownProcMacroKind.ASYNC_MAIN,
        "test" to KnownProcMacroKind.ASYNC_TEST,
        "bench" to KnownProcMacroKind.ASYNC_BENCH,
    ),
    "tracing_attributes" to mapOf("instrument" to KnownProcMacroKind.IDENTITY),
    "proc_macro_error_attr" to mapOf("proc_macro_error" to KnownProcMacroKind.IDENTITY),
    "actix_web_codegen" to mapOf("main" to KnownProcMacroKind.ASYNC_MAIN),
    "actix_derive" to mapOf(
        "main" to KnownProcMacroKind.ASYNC_MAIN,
        "test" to KnownProcMacroKind.ASYNC_TEST,
    ),
    "serial_test_derive" to mapOf("serial" to KnownProcMacroKind.TEST_WRAPPER),
    "cortex_m_rt_macros" to mapOf("entry" to KnownProcMacroKind.CUSTOM_MAIN),
    "test_case" to mapOf("test_case" to KnownProcMacroKind.CUSTOM_TEST),
    "ndk_macro" to mapOf("main" to KnownProcMacroKind.CUSTOM_MAIN),
    "quickcheck_macros" to mapOf("quickcheck" to KnownProcMacroKind.CUSTOM_TEST),
    "async_recursion" to mapOf("async_recursion" to KnownProcMacroKind.IDENTITY),
    "paw_attributes" to mapOf("main" to KnownProcMacroKind.CUSTOM_MAIN),
    "interpolate_name" to mapOf("interpolate_test" to KnownProcMacroKind.CUSTOM_TEST_RENAME),
    "ntest_test_cases" to mapOf("test_case" to KnownProcMacroKind.CUSTOM_TEST_RENAME),
    "spandoc_attribute" to mapOf("spandoc" to KnownProcMacroKind.IDENTITY),
    "log_derive" to mapOf(
        "logfn" to KnownProcMacroKind.IDENTITY,
        "logfn_inputs" to KnownProcMacroKind.IDENTITY,
    ),
    "wasm_bindgen_test_macro" to mapOf("wasm_bindgen_test" to KnownProcMacroKind.CUSTOM_TEST),
    "test_env_log" to mapOf("test" to KnownProcMacroKind.CUSTOM_TEST),
    "parameterized_macro" to mapOf("parameterized" to KnownProcMacroKind.CUSTOM_TEST_RENAME),
    "alloc_counter_macro" to mapOf(
        "no_alloc" to KnownProcMacroKind.IDENTITY,
        "count_alloc" to KnownProcMacroKind.IDENTITY,
    ),
    "uefi_macros" to mapOf("entry" to KnownProcMacroKind.CUSTOM_MAIN),
    "async_trait" to mapOf("async_trait" to KnownProcMacroKind.ASYNC_TRAIT),
)

fun getHardcodeProcMacroProperties(packageName: String, macroName: String): KnownProcMacroKind {
    val kind = RS_HARDCODED_PROC_MACRO_ATTRIBUTES[packageName]?.get(macroName)
    if (kind != null) {
        return kind
    }

    if (isUnitTestMode && packageName == "test_proc_macros" && macroName == "attr_hardcoded_not_a_macro") {
        return KnownProcMacroKind.IDENTITY
    }

    return KnownProcMacroKind.DEFAULT_PURE
}

/**
 * Defines some kinds of already_known (hardcoded) behavior of some procedural macros.
 *
 * @see RS_HARDCODED_PROC_MACRO_ATTRIBUTES
 */
enum class KnownProcMacroKind {
    /** No special behavior */
    DEFAULT_PURE,

    /** The expansion of a macro is the same as the input, i.e. the macro is a no-op */
    IDENTITY,

    /**
     * The macro defines an async `main` function. The signature of such function is
     * similar to a usual `main` function except it requires `async` keyword.
     * A code inside the function behaves like a usual code in an async context.
     */
    ASYNC_MAIN,

    /**
     * The macro defines an async test (`#[test]`) function. The signature of such
     * function is similar to a usual test function except it requires `async` keyword.
     * A code inside the function behaves like a usual code in an async context.
     */
    ASYNC_TEST,

    /**
     * The macro defines an async benchmark (`#[bench]`) function. The signature of such
     * function is similar to a usual bench function except it requires `async` keyword.
     * A code inside the function behaves like a usual code in an async context.
     */
    ASYNC_BENCH,

    /** Some additional attribute for test functions. Used in addition to `#[test]` attribute */
    TEST_WRAPPER,

    /** The macro defines an entry point (`main`) function with arbitrary name and signature. */
    CUSTOM_MAIN,

    /** The macro defines a test function with arbitrary signature */
    CUSTOM_TEST,

    /**
     * The macro defines a test function with arbitrary signature and also renames that function.
     * Also, the macro can produce multiple test functions.
     */
    CUSTOM_TEST_RENAME,

    /** https://crates.io/crates/async-trait */
    ASYNC_TRAIT,
    ;

    val treatAsBuiltinAttr: Boolean
        get() = this != DEFAULT_PURE
}
