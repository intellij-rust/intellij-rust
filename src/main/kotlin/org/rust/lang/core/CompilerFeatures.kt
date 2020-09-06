/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("unused")

package org.rust.lang.core

import org.rust.lang.core.FeatureState.ACCEPTED
import org.rust.lang.core.FeatureState.ACTIVE

// -------------------------------------------------------------------------
// feature-group-start: internal feature gates
// -------------------------------------------------------------------------

// no-tracking-issue-start

// Allows using `rustc_*` attributes (RFC 572).
val RUSTC_ATTRS = CompilerFeature("rustc_attrs", ACTIVE, "1.0.0")
// Allows using compiler's own crates.
val RUSTC_PRIVATE = CompilerFeature("rustc_private", ACTIVE, "1.0.0")
// Allows using the `rust-intrinsic`'s "ABI".
val INTRINSICS = CompilerFeature("intrinsics", ACTIVE, "1.0.0")
// Allows using `#[lang = ".."]` attribute for linking items to special compiler logic.
val LANG_ITEMS = CompilerFeature("lang_items", ACTIVE, "1.0.0")
// Allows using the `#[stable]` and `#[unstable]` attributes.
val STAGED_API = CompilerFeature("staged_api", ACTIVE, "1.0.0")
// Allows using `#[allow_internal_unstable]`. This is an
// attribute on `macro_rules!` and can't use the attribute handling
// below (it has to be checked before expansion possibly makes
// macros disappear).
val ALLOW_INTERNAL_UNSTABLE = CompilerFeature("allow_internal_unstable", ACTIVE, "1.0.0")
// Allows using `#[allow_internal_unsafe]`. This is an
// attribute on `macro_rules!` and can't use the attribute handling
// below (it has to be checked before expansion possibly makes
// macros disappear).
val ALLOW_INTERNAL_UNSAFE = CompilerFeature("allow_internal_unsafe", ACTIVE, "1.0.0")
// no-tracking-issue-end

// Allows using `#[link_name="llvm.*"]`.
val LINK_LLVM_INTRINSICS = CompilerFeature("link_llvm_intrinsics", ACTIVE, "1.0.0")
// Allows using the `box $expr` syntax.
val BOX_SYNTAX = CompilerFeature("box_syntax", ACTIVE, "1.0.0")
// Allows using `#[main]` to replace the entrypoint `#[lang = "start"]` calls.
val MAIN = CompilerFeature("main", ACTIVE, "1.0.0")
// Allows using `#[start]` on a function indicating that it is the program entrypoint.
val START = CompilerFeature("start", ACTIVE, "1.0.0")
// Allows using the `#[fundamental]` attribute.
val FUNDAMENTAL = CompilerFeature("fundamental", ACTIVE, "1.0.0")
// Allows using the `rust-call` ABI.
val UNBOXED_CLOSURES = CompilerFeature("unboxed_closures", ACTIVE, "1.0.0")
// Allows using the `#[linkage = ".."]` attribute.
val LINKAGE = CompilerFeature("linkage", ACTIVE, "1.0.0")
// Allows features specific to OIBIT (auto traits).
val OPTIN_BUILTIN_TRAITS = CompilerFeature("optin_builtin_traits", ACTIVE, "1.0.0")
// Allows using `box` in patterns (RFC 469).
val BOX_PATTERNS = CompilerFeature("box_patterns", ACTIVE, "1.0.0")
// no-tracking-issue-start

// Allows using `#[prelude_import]` on glob `use` items.
val PRELUDE_IMPORT = CompilerFeature("prelude_import", ACTIVE, "1.2.0")
// no-tracking-issue-end

// no-tracking-issue-start

// Allows using `#[omit_gdb_pretty_printer_section]`.
val OMIT_GDB_PRETTY_PRINTER_SECTION = CompilerFeature("omit_gdb_pretty_printer_section", ACTIVE, "1.5.0")
// Allows using the `vectorcall` ABI.
val ABI_VECTORCALL = CompilerFeature("abi_vectorcall", ACTIVE, "1.7.0")
// no-tracking-issue-end

// Allows using `#[structural_match]` which indicates that a type is structurally matchable.
// FIXME: Subsumed by trait `StructuralPartialEq`, cannot move to removed until a library
// feature with the same name exists.
val STRUCTURAL_MATCH = CompilerFeature("structural_match", ACTIVE, "1.8.0")
// Allows using the `may_dangle` attribute (RFC 1327).
val DROPCK_EYEPATCH = CompilerFeature("dropck_eyepatch", ACTIVE, "1.10.0")
// Allows using the `#![panic_runtime]` attribute.
val PANIC_RUNTIME = CompilerFeature("panic_runtime", ACTIVE, "1.10.0")
// Allows declaring with `#![needs_panic_runtime]` that a panic runtime is needed.
val NEEDS_PANIC_RUNTIME = CompilerFeature("needs_panic_runtime", ACTIVE, "1.10.0")
// no-tracking-issue-start

// Allows identifying the `compiler_builtins` crate.
val COMPILER_BUILTINS = CompilerFeature("compiler_builtins", ACTIVE, "1.13.0")
// Allows using the `unadjusted` ABI; perma-unstable.
val ABI_UNADJUSTED = CompilerFeature("abi_unadjusted", ACTIVE, "1.16.0")
// Used to identify crates that contain the profiler runtime.
val PROFILER_RUNTIME = CompilerFeature("profiler_runtime", ACTIVE, "1.18.0")
// Allows using the `thiscall` ABI.
val ABI_THISCALL = CompilerFeature("abi_thiscall", ACTIVE, "1.19.0")
// Allows using `#![needs_allocator]`, an implementation detail of `#[global_allocator]`.
val ALLOCATOR_INTERNALS = CompilerFeature("allocator_internals", ACTIVE, "1.20.0")
// Added for testing E0705; perma-unstable.
val TEST_2018_FEATURE = CompilerFeature("test_2018_feature", ACTIVE, "1.31.0")
// Allows `#[repr(no_niche)]` (an implementation detail of `rustc`,
// it is not on path for eventual stabilization).
val NO_NICHE = CompilerFeature("no_niche", ACTIVE, "1.42.0")
// no-tracking-issue-end

// -------------------------------------------------------------------------
// feature-group-end: internal feature gates
// -------------------------------------------------------------------------

// -------------------------------------------------------------------------
// feature-group-start: actual feature gates (target features)
// -------------------------------------------------------------------------

// FIXME: Document these and merge with the list below.

// Unstable `#[target_feature]` directives.
val ARM_TARGET_FEATURE = CompilerFeature("arm_target_feature", ACTIVE, "1.27.0")
val AARCH64_TARGET_FEATURE = CompilerFeature("aarch64_target_feature", ACTIVE, "1.27.0")
val HEXAGON_TARGET_FEATURE = CompilerFeature("hexagon_target_feature", ACTIVE, "1.27.0")
val POWERPC_TARGET_FEATURE = CompilerFeature("powerpc_target_feature", ACTIVE, "1.27.0")
val MIPS_TARGET_FEATURE = CompilerFeature("mips_target_feature", ACTIVE, "1.27.0")
val AVX512_TARGET_FEATURE = CompilerFeature("avx512_target_feature", ACTIVE, "1.27.0")
val MMX_TARGET_FEATURE = CompilerFeature("mmx_target_feature", ACTIVE, "1.27.0")
val SSE4A_TARGET_FEATURE = CompilerFeature("sse4a_target_feature", ACTIVE, "1.27.0")
val TBM_TARGET_FEATURE = CompilerFeature("tbm_target_feature", ACTIVE, "1.27.0")
val WASM_TARGET_FEATURE = CompilerFeature("wasm_target_feature", ACTIVE, "1.30.0")
val ADX_TARGET_FEATURE = CompilerFeature("adx_target_feature", ACTIVE, "1.32.0")
val CMPXCHG16B_TARGET_FEATURE = CompilerFeature("cmpxchg16b_target_feature", ACTIVE, "1.32.0")
val MOVBE_TARGET_FEATURE = CompilerFeature("movbe_target_feature", ACTIVE, "1.34.0")
val RTM_TARGET_FEATURE = CompilerFeature("rtm_target_feature", ACTIVE, "1.35.0")
val F16C_TARGET_FEATURE = CompilerFeature("f16c_target_feature", ACTIVE, "1.36.0")
val RISCV_TARGET_FEATURE = CompilerFeature("riscv_target_feature", ACTIVE, "1.45.0")
// -------------------------------------------------------------------------
// feature-group-end: actual feature gates (target features)
// -------------------------------------------------------------------------

// -------------------------------------------------------------------------
// feature-group-start: actual feature gates
// -------------------------------------------------------------------------

// Allows using the `#[link_args]` attribute.
val LINK_ARGS = CompilerFeature("link_args", ACTIVE, "1.0.0")
// Allows defining identifiers beyond ASCII.
val NON_ASCII_IDENTS = CompilerFeature("non_ascii_idents", ACTIVE, "1.0.0")
// Allows using `#[plugin_registrar]` on functions.
val PLUGIN_REGISTRAR = CompilerFeature("plugin_registrar", ACTIVE, "1.0.0")
// Allows using `#![plugin(myplugin)]`.
val PLUGIN = CompilerFeature("plugin", ACTIVE, "1.0.0")
// Allows using `#[thread_local]` on `static` items.
val THREAD_LOCAL = CompilerFeature("thread_local", ACTIVE, "1.0.0")
// Allows the use of SIMD types in functions declared in `extern` blocks.
val SIMD_FFI = CompilerFeature("simd_ffi", ACTIVE, "1.0.0")
// Allows using non lexical lifetimes (RFC 2094).
val NLL = CompilerFeature("nll", ACTIVE, "1.0.0")
// Allows the definition of `const` functions with some advanced features.
val CONST_FN = CompilerFeature("const_fn", ACTIVE, "1.2.0")
// Allows associated type defaults.
val ASSOCIATED_TYPE_DEFAULTS = CompilerFeature("associated_type_defaults", ACTIVE, "1.2.0")
// Allows `#![no_core]`.
val NO_CORE = CompilerFeature("no_core", ACTIVE, "1.3.0")
// Allows default type parameters to influence type inference.
val DEFAULT_TYPE_PARAMETER_FALLBACK = CompilerFeature("default_type_parameter_fallback", ACTIVE, "1.3.0")
// Allows `repr(simd)` and importing the various simd intrinsics.
val REPR_SIMD = CompilerFeature("repr_simd", ACTIVE, "1.4.0")
// Allows `extern "platform-intrinsic" { ... }`.
val PLATFORM_INTRINSICS = CompilerFeature("platform_intrinsics", ACTIVE, "1.4.0")
// Allows `#[unwind(..)]`.
//
// Permits specifying whether a function should permit unwinding or abort on unwind.
val UNWIND_ATTRIBUTES = CompilerFeature("unwind_attributes", ACTIVE, "1.4.0")
// Allows attributes on expressions and non-item statements.
val STMT_EXPR_ATTRIBUTES = CompilerFeature("stmt_expr_attributes", ACTIVE, "1.6.0")
// Allows the use of type ascription in expressions.
val TYPE_ASCRIPTION = CompilerFeature("type_ascription", ACTIVE, "1.6.0")
// Allows `cfg(target_thread_local)`.
val CFG_TARGET_THREAD_LOCAL = CompilerFeature("cfg_target_thread_local", ACTIVE, "1.7.0")
// Allows specialization of implementations (RFC 1210).
val SPECIALIZATION = CompilerFeature("specialization", ACTIVE, "1.7.0")
// A minimal, sound subset of specialization intended to be used by the
// standard library until the soundness issues with specialization
// are fixed.
val MIN_SPECIALIZATION = CompilerFeature("min_specialization", ACTIVE, "1.7.0")
// Allows using `#[naked]` on functions.
val NAKED_FUNCTIONS = CompilerFeature("naked_functions", ACTIVE, "1.9.0")
// Allows `cfg(target_has_atomic = "...")`.
val CFG_TARGET_HAS_ATOMIC = CompilerFeature("cfg_target_has_atomic", ACTIVE, "1.9.0")
// Allows `X..Y` patterns.
val EXCLUSIVE_RANGE_PATTERN = CompilerFeature("exclusive_range_pattern", ACTIVE, "1.11.0")
// Allows the `!` type. Does not imply 'exhaustive_patterns' (below) any more.
val NEVER_TYPE = CompilerFeature("never_type", ACTIVE, "1.13.0")
// Allows exhaustive pattern matching on types that contain uninhabited types.
val EXHAUSTIVE_PATTERNS = CompilerFeature("exhaustive_patterns", ACTIVE, "1.13.0")
// Allows `union`s to implement `Drop`. Moreover, `union`s may now include fields
// that don't implement `Copy` as long as they don't have any drop glue.
// This is checked recursively. On encountering type variable where no progress can be made,
// `T: Copy` is used as a substitute for "no drop glue".
//
// NOTE: A limited form of `union U { ... }` was accepted in 1.19.0.
val UNTAGGED_UNIONS = CompilerFeature("untagged_unions", ACTIVE, "1.13.0")
// Allows `#[link(..., cfg(..))]`.
val LINK_CFG = CompilerFeature("link_cfg", ACTIVE, "1.14.0")
// Allows `extern "ptx-*" fn()`.
val ABI_PTX = CompilerFeature("abi_ptx", ACTIVE, "1.15.0")
// Allows the `#[repr(i128)]` attribute for enums.
val REPR128 = CompilerFeature("repr128", ACTIVE, "1.16.0")
// Allows `#[link(kind="static-nobundle"...)]`.
val STATIC_NOBUNDLE = CompilerFeature("static_nobundle", ACTIVE, "1.16.0")
// Allows `extern "msp430-interrupt" fn()`.
val ABI_MSP430_INTERRUPT = CompilerFeature("abi_msp430_interrupt", ACTIVE, "1.16.0")
// Allows declarative macros 2.0 (`macro`).
val DECL_MACRO = CompilerFeature("decl_macro", ACTIVE, "1.17.0")
// Allows `extern "x86-interrupt" fn()`.
val ABI_X86_INTERRUPT = CompilerFeature("abi_x86_interrupt", ACTIVE, "1.17.0")
// Allows a test to fail without failing the whole suite.
val ALLOW_FAIL = CompilerFeature("allow_fail", ACTIVE, "1.19.0")
// Allows unsized tuple coercion.
val UNSIZED_TUPLE_COERCION = CompilerFeature("unsized_tuple_coercion", ACTIVE, "1.20.0")
// Allows defining generators.
val GENERATORS = CompilerFeature("generators", ACTIVE, "1.21.0")
// Allows `#[doc(cfg(...))]`.
val DOC_CFG = CompilerFeature("doc_cfg", ACTIVE, "1.21.0")
// Allows `#[doc(masked)]`.
val DOC_MASKED = CompilerFeature("doc_masked", ACTIVE, "1.21.0")
// Allows `#[doc(spotlight)]`.
val DOC_SPOTLIGHT = CompilerFeature("doc_spotlight", ACTIVE, "1.22.0")
// Allows `#[doc(include = "some-file")]`.
val EXTERNAL_DOC = CompilerFeature("external_doc", ACTIVE, "1.22.0")
// Allows using `crate` as visibility modifier, synonymous with `pub(crate)`.
val CRATE_VISIBILITY_MODIFIER = CompilerFeature("crate_visibility_modifier", ACTIVE, "1.23.0")
// Allows defining `extern type`s.
val EXTERN_TYPES = CompilerFeature("extern_types", ACTIVE, "1.23.0")
// Allows trait methods with arbitrary self types.
val ARBITRARY_SELF_TYPES = CompilerFeature("arbitrary_self_types", ACTIVE, "1.23.0")
// Allows in-band quantification of lifetime bindings (e.g., `fn foo(x: &'a u8) -> &'a u8`).
val IN_BAND_LIFETIMES = CompilerFeature("in_band_lifetimes", ACTIVE, "1.23.0")
// Allows associated types to be generic, e.g., `type Foo<T>;` (RFC 1598).
val GENERIC_ASSOCIATED_TYPES = CompilerFeature("generic_associated_types", ACTIVE, "1.23.0")
// Allows defining `trait X = A + B;` alias items.
val TRAIT_ALIAS = CompilerFeature("trait_alias", ACTIVE, "1.24.0")
// Allows inferring `'static` outlives requirements (RFC 2093).
val INFER_STATIC_OUTLIVES_REQUIREMENTS = CompilerFeature("infer_static_outlives_requirements", ACTIVE, "1.26.0")
// Allows accessing fields of unions inside `const` functions.
val CONST_FN_UNION = CompilerFeature("const_fn_union", ACTIVE, "1.27.0")
// Allows casting raw pointers to `usize` during const eval.
val CONST_RAW_PTR_TO_USIZE_CAST = CompilerFeature("const_raw_ptr_to_usize_cast", ACTIVE, "1.27.0")
// Allows dereferencing raw pointers during const eval.
val CONST_RAW_PTR_DEREF = CompilerFeature("const_raw_ptr_deref", ACTIVE, "1.27.0")
// Allows `#[doc(alias = "...")]`.
val DOC_ALIAS = CompilerFeature("doc_alias", ACTIVE, "1.27.0")
// Allows inconsistent bounds in where clauses.
val TRIVIAL_BOUNDS = CompilerFeature("trivial_bounds", ACTIVE, "1.28.0")
// Allows `'a: { break 'a; }`.
val LABEL_BREAK_VALUE = CompilerFeature("label_break_value", ACTIVE, "1.28.0")
// Allows using `#[doc(keyword = "...")]`.
val DOC_KEYWORD = CompilerFeature("doc_keyword", ACTIVE, "1.28.0")
// Allows using `try {...}` expressions.
val TRY_BLOCKS = CompilerFeature("try_blocks", ACTIVE, "1.29.0")
// Allows defining an `#[alloc_error_handler]`.
val ALLOC_ERROR_HANDLER = CompilerFeature("alloc_error_handler", ACTIVE, "1.29.0")
// Allows using the `amdgpu-kernel` ABI.
val ABI_AMDGPU_KERNEL = CompilerFeature("abi_amdgpu_kernel", ACTIVE, "1.29.0")
// Allows panicking during const eval (producing compile-time errors).
val CONST_PANIC = CompilerFeature("const_panic", ACTIVE, "1.30.0")
// Allows `#[marker]` on certain traits allowing overlapping implementations.
val MARKER_TRAIT_ATTR = CompilerFeature("marker_trait_attr", ACTIVE, "1.30.0")
// Allows macro attributes on expressions, statements and non-inline modules.
val PROC_MACRO_HYGIENE = CompilerFeature("proc_macro_hygiene", ACTIVE, "1.30.0")
// Allows unsized rvalues at arguments and parameters.
val UNSIZED_LOCALS = CompilerFeature("unsized_locals", ACTIVE, "1.30.0")
// Allows custom test frameworks with `#![test_runner]` and `#[test_case]`.
val CUSTOM_TEST_FRAMEWORKS = CompilerFeature("custom_test_frameworks", ACTIVE, "1.30.0")
// Allows non-builtin attributes in inner attribute position.
val CUSTOM_INNER_ATTRIBUTES = CompilerFeature("custom_inner_attributes", ACTIVE, "1.30.0")
// Allows `impl Trait` in bindings (`let`, `const`, `static`).
val IMPL_TRAIT_IN_BINDINGS = CompilerFeature("impl_trait_in_bindings", ACTIVE, "1.30.0")
// Allows using `reason` in lint attributes and the `#[expect(lint)]` lint check.
val LINT_REASONS = CompilerFeature("lint_reasons", ACTIVE, "1.31.0")
// Allows exhaustive integer pattern matching on `usize` and `isize`.
val PRECISE_POINTER_SIZE_MATCHING = CompilerFeature("precise_pointer_size_matching", ACTIVE, "1.32.0")
// Allows using `#[ffi_returns_twice]` on foreign functions.
val FFI_RETURNS_TWICE = CompilerFeature("ffi_returns_twice", ACTIVE, "1.34.0")
// Allows const generic types (e.g. `struct Foo<const N: usize>(...);`).
val CONST_GENERICS = CompilerFeature("const_generics", ACTIVE, "1.34.0")
// Allows using `#[optimize(X)]`.
val OPTIMIZE_ATTRIBUTE = CompilerFeature("optimize_attribute", ACTIVE, "1.34.0")
// Allows using C-variadics.
val C_VARIADIC = CompilerFeature("c_variadic", ACTIVE, "1.34.0")
// Allows the user of associated type bounds.
val ASSOCIATED_TYPE_BOUNDS = CompilerFeature("associated_type_bounds", ACTIVE, "1.34.0")
// Allows `if/while p && let q = r && ...` chains.
val LET_CHAINS = CompilerFeature("let_chains", ACTIVE, "1.37.0")
// Allows #[repr(transparent)] on unions (RFC 2645).
val TRANSPARENT_UNIONS = CompilerFeature("transparent_unions", ACTIVE, "1.37.0")
// Allows explicit discriminants on non-unit enum variants.
val ARBITRARY_ENUM_DISCRIMINANT = CompilerFeature("arbitrary_enum_discriminant", ACTIVE, "1.37.0")
// Allows `impl Trait` with multiple unrelated lifetimes.
val MEMBER_CONSTRAINTS = CompilerFeature("member_constraints", ACTIVE, "1.37.0")
// Allows `async || body` closures.
val ASYNC_CLOSURE = CompilerFeature("async_closure", ACTIVE, "1.37.0")
// Allows `[x; N]` where `x` is a constant (RFC 2203).
val CONST_IN_ARRAY_REPEAT_EXPRESSIONS = CompilerFeature("const_in_array_repeat_expressions", ACTIVE, "1.37.0")
// Allows `impl Trait` to be used inside type aliases (RFC 2515).
val TYPE_ALIAS_IMPL_TRAIT = CompilerFeature("type_alias_impl_trait", ACTIVE, "1.38.0")
// Allows the use of or-patterns (e.g., `0 | 1`).
val OR_PATTERNS = CompilerFeature("or_patterns", ACTIVE, "1.38.0")
// Allows the definition of `const extern fn` and `const unsafe extern fn`.
val CONST_EXTERN_FN = CompilerFeature("const_extern_fn", ACTIVE, "1.40.0")
// Allows the use of raw-dylibs (RFC 2627).
val RAW_DYLIB = CompilerFeature("raw_dylib", ACTIVE, "1.40.0")
// Allows making `dyn Trait` well-formed even if `Trait` is not object safe.
// In that case, `dyn Trait: Trait` does not hold. Moreover, coercions and
// casts in safe Rust to `dyn Trait` for such a `Trait` is also forbidden.
val OBJECT_SAFE_FOR_DISPATCH = CompilerFeature("object_safe_for_dispatch", ACTIVE, "1.40.0")
// Allows using the `efiapi` ABI.
val ABI_EFIAPI = CompilerFeature("abi_efiapi", ACTIVE, "1.40.0")
// Allows `&raw const $place_expr` and `&raw mut $place_expr` expressions.
val RAW_REF_OP = CompilerFeature("raw_ref_op", ACTIVE, "1.41.0")
// Allows diverging expressions to fall back to `!` rather than `()`.
val NEVER_TYPE_FALLBACK = CompilerFeature("never_type_fallback", ACTIVE, "1.41.0")
// Allows using the `#[register_attr]` attribute.
val REGISTER_ATTR = CompilerFeature("register_attr", ACTIVE, "1.41.0")
// Allows using the `#[register_tool]` attribute.
val REGISTER_TOOL = CompilerFeature("register_tool", ACTIVE, "1.41.0")
// Allows the use of `#[cfg(sanitize = "option")]`; set when -Zsanitizer is used.
val CFG_SANITIZE = CompilerFeature("cfg_sanitize", ACTIVE, "1.41.0")
// Allows using `..X`, `..=X`, `...X`, and `X..` as a pattern.
val HALF_OPEN_RANGE_PATTERNS = CompilerFeature("half_open_range_patterns", ACTIVE, "1.41.0")
// Allows using `&mut` in constant functions.
val CONST_MUT_REFS = CompilerFeature("const_mut_refs", ACTIVE, "1.41.0")
// Allows bindings in the subpattern of a binding pattern.
// For example, you can write `x @ Some(y)`.
val BINDINGS_AFTER_AT = CompilerFeature("bindings_after_at", ACTIVE, "1.41.0")
// Allows patterns with concurrent by-move and by-ref bindings.
// For example, you can write `Foo(a, ref b)` where `a` is by-move and `b` is by-ref.
val MOVE_REF_PATTERN = CompilerFeature("move_ref_pattern", ACTIVE, "1.42.0")
// Allows `impl const Trait for T` syntax.
val CONST_TRAIT_IMPL = CompilerFeature("const_trait_impl", ACTIVE, "1.42.0")
// Allows `T: ?const Trait` syntax in bounds.
val CONST_TRAIT_BOUND_OPT_OUT = CompilerFeature("const_trait_bound_opt_out", ACTIVE, "1.42.0")
// Allows the use of `no_sanitize` attribute.
val NO_SANITIZE = CompilerFeature("no_sanitize", ACTIVE, "1.42.0")
// Allows limiting the evaluation steps of const expressions
val CONST_EVAL_LIMIT = CompilerFeature("const_eval_limit", ACTIVE, "1.43.0")
// Allow negative trait implementations.
val NEGATIVE_IMPLS = CompilerFeature("negative_impls", ACTIVE, "1.44.0")
// Allows the use of `#[target_feature]` on safe functions.
val TARGET_FEATURE_11 = CompilerFeature("target_feature_11", ACTIVE, "1.45.0")
// Allow conditional compilation depending on rust version
val CFG_VERSION = CompilerFeature("cfg_version", ACTIVE, "1.45.0")
// Allows the use of `#[ffi_pure]` on foreign functions.
val FFI_PURE = CompilerFeature("ffi_pure", ACTIVE, "1.45.0")
// Allows the use of `#[ffi_const]` on foreign functions.
val FFI_CONST = CompilerFeature("ffi_const", ACTIVE, "1.45.0")
// No longer treat an unsafe function as an unsafe block.
val UNSAFE_BLOCK_IN_UNSAFE_FN = CompilerFeature("unsafe_block_in_unsafe_fn", ACTIVE, "1.45.0")
// Allows `extern "avr-interrupt" fn()` and `extern "avr-non-blocking-interrupt" fn()`.
val ABI_AVR_INTERRUPT = CompilerFeature("abi_avr_interrupt", ACTIVE, "1.45.0")
// Be more precise when looking for live drops in a const context.
val CONST_PRECISE_LIVE_DROPS = CompilerFeature("const_precise_live_drops", ACTIVE, "1.46.0")
// Allows capturing variables in scope using format_args!
val FORMAT_ARGS_CAPTURE = CompilerFeature("format_args_capture", ACTIVE, "1.46.0")
// Lazily evaluate constants. This allows constants to depend on type parameters.
val LAZY_NORMALIZATION_CONSTS = CompilerFeature("lazy_normalization_consts", ACTIVE, "1.46.0")
// Allows calling `transmute` in const fn
val CONST_FN_TRANSMUTE = CompilerFeature("const_fn_transmute", ACTIVE, "1.46.0")
// The smallest useful subset of `const_generics`.
val MIN_CONST_GENERICS = CompilerFeature("min_const_generics", ACTIVE, "1.47.0")
// Allows `if let` guard in match arms.
val IF_LET_GUARD = CompilerFeature("if_let_guard", ACTIVE, "1.47.0")

// -------------------------------------------------------------------------
// feature-group-start: for testing purposes
// -------------------------------------------------------------------------

// A temporary feature gate used to enable parser extensions needed
// to bootstrap fix for #5723.
val ISSUE_5723_BOOTSTRAP = CompilerFeature("issue_5723_bootstrap", ACCEPTED, "1.0.0")
// These are used to test this portion of the compiler,
// they don't actually mean anything.
val TEST_ACCEPTED_FEATURE = CompilerFeature("test_accepted_feature", ACCEPTED, "1.0.0")
// -------------------------------------------------------------------------
// feature-group-end: for testing purposes
// -------------------------------------------------------------------------

// -------------------------------------------------------------------------
// feature-group-start: accepted features
// -------------------------------------------------------------------------

// Allows using associated `type`s in `trait`s.
val ASSOCIATED_TYPES = CompilerFeature("associated_types", ACCEPTED, "1.0.0")
// Allows using assigning a default type to type parameters in algebraic data type definitions.
val DEFAULT_TYPE_PARAMS = CompilerFeature("default_type_params", ACCEPTED, "1.0.0")
// FIXME: explain `globs`.
val GLOBS = CompilerFeature("globs", ACCEPTED, "1.0.0")
// Allows `macro_rules!` items.
val MACRO_RULES = CompilerFeature("macro_rules", ACCEPTED, "1.0.0")
// Allows use of `&foo[a..b]` as a slicing syntax.
val SLICING_SYNTAX = CompilerFeature("slicing_syntax", ACCEPTED, "1.0.0")
// Allows struct variants `Foo { baz: u8, .. }` in enums (RFC 418).
val STRUCT_VARIANT = CompilerFeature("struct_variant", ACCEPTED, "1.0.0")
// Allows indexing tuples.
val TUPLE_INDEXING = CompilerFeature("tuple_indexing", ACCEPTED, "1.0.0")
// Allows the use of `if let` expressions.
val IF_LET = CompilerFeature("if_let", ACCEPTED, "1.0.0")
// Allows the use of `while let` expressions.
val WHILE_LET = CompilerFeature("while_let", ACCEPTED, "1.0.0")
// Allows using `#![no_std]`.
val NO_STD = CompilerFeature("no_std", ACCEPTED, "1.6.0")
// Allows overloading augmented assignment operations like `a += b`.
val AUGMENTED_ASSIGNMENTS = CompilerFeature("augmented_assignments", ACCEPTED, "1.8.0")
// Allows empty structs and enum variants with braces.
val BRACED_EMPTY_STRUCTS = CompilerFeature("braced_empty_structs", ACCEPTED, "1.8.0")
// Allows `#[deprecated]` attribute.
val DEPRECATED = CompilerFeature("deprecated", ACCEPTED, "1.9.0")
// Allows macros to appear in the type position.
val TYPE_MACROS = CompilerFeature("type_macros", ACCEPTED, "1.13.0")
// Allows use of the postfix `?` operator in expressions.
val QUESTION_MARK = CompilerFeature("question_mark", ACCEPTED, "1.13.0")
// Allows `..` in tuple (struct) patterns.
val DOTDOT_IN_TUPLE_PATTERNS = CompilerFeature("dotdot_in_tuple_patterns", ACCEPTED, "1.14.0")
// Allows some increased flexibility in the name resolution rules,
// especially around globs and shadowing (RFC 1560).
val ITEM_LIKE_IMPORTS = CompilerFeature("item_like_imports", ACCEPTED, "1.15.0")
// Allows using `Self` and associated types in struct expressions and patterns.
val MORE_STRUCT_ALIASES = CompilerFeature("more_struct_aliases", ACCEPTED, "1.16.0")
// Allows elision of `'static` lifetimes in `static`s and `const`s.
val STATIC_IN_CONST = CompilerFeature("static_in_const", ACCEPTED, "1.17.0")
// Allows field shorthands (`x` meaning `x: x`) in struct literal expressions.
val FIELD_INIT_SHORTHAND = CompilerFeature("field_init_shorthand", ACCEPTED, "1.17.0")
// Allows the definition recursive static items.
val STATIC_RECURSION = CompilerFeature("static_recursion", ACCEPTED, "1.17.0")
// Allows `pub(restricted)` visibilities (RFC 1422).
val PUB_RESTRICTED = CompilerFeature("pub_restricted", ACCEPTED, "1.18.0")
// Allows `#![windows_subsystem]`.
val WINDOWS_SUBSYSTEM = CompilerFeature("windows_subsystem", ACCEPTED, "1.18.0")
// Allows `break {expr}` with a value inside `loop`s.
val LOOP_BREAK_VALUE = CompilerFeature("loop_break_value", ACCEPTED, "1.19.0")
// Allows numeric fields in struct expressions and patterns.
val RELAXED_ADTS = CompilerFeature("relaxed_adts", ACCEPTED, "1.19.0")
// Allows coercing non capturing closures to function pointers.
val CLOSURE_TO_FN_COERCION = CompilerFeature("closure_to_fn_coercion", ACCEPTED, "1.19.0")
// Allows attributes on struct literal fields.
val STRUCT_FIELD_ATTRIBUTES = CompilerFeature("struct_field_attributes", ACCEPTED, "1.20.0")
// Allows the definition of associated constants in `trait` or `impl` blocks.
val ASSOCIATED_CONSTS = CompilerFeature("associated_consts", ACCEPTED, "1.20.0")
// Allows usage of the `compile_error!` macro.
val COMPILE_ERROR = CompilerFeature("compile_error", ACCEPTED, "1.20.0")
// Allows code like `let x: &'static u32 = &42` to work (RFC 1414).
val RVALUE_STATIC_PROMOTION = CompilerFeature("rvalue_static_promotion", ACCEPTED, "1.21.0")
// Allows `Drop` types in constants (RFC 1440).
val DROP_TYPES_IN_CONST = CompilerFeature("drop_types_in_const", ACCEPTED, "1.22.0")
// Allows the sysV64 ABI to be specified on all platforms
// instead of just the platforms on which it is the C ABI.
val ABI_SYSV64 = CompilerFeature("abi_sysv64", ACCEPTED, "1.24.0")
// Allows `repr(align(16))` struct attribute (RFC 1358).
val REPR_ALIGN = CompilerFeature("repr_align", ACCEPTED, "1.25.0")
// Allows '|' at beginning of match arms (RFC 1925).
val MATCH_BEGINNING_VERT = CompilerFeature("match_beginning_vert", ACCEPTED, "1.25.0")
// Allows nested groups in `use` items (RFC 2128).
val USE_NESTED_GROUPS = CompilerFeature("use_nested_groups", ACCEPTED, "1.25.0")
// Allows indexing into constant arrays.
val CONST_INDEXING = CompilerFeature("const_indexing", ACCEPTED, "1.26.0")
// Allows using `a..=b` and `..=b` as inclusive range syntaxes.
val INCLUSIVE_RANGE_SYNTAX = CompilerFeature("inclusive_range_syntax", ACCEPTED, "1.26.0")
// Allows `..=` in patterns (RFC 1192).
val DOTDOTEQ_IN_PATTERNS = CompilerFeature("dotdoteq_in_patterns", ACCEPTED, "1.26.0")
// Allows `fn main()` with return types which implements `Termination` (RFC 1937).
val TERMINATION_TRAIT = CompilerFeature("termination_trait", ACCEPTED, "1.26.0")
// Allows implementing `Clone` for closures where possible (RFC 2132).
val CLONE_CLOSURES = CompilerFeature("clone_closures", ACCEPTED, "1.26.0")
// Allows implementing `Copy` for closures where possible (RFC 2132).
val COPY_CLOSURES = CompilerFeature("copy_closures", ACCEPTED, "1.26.0")
// Allows `impl Trait` in function arguments.
val UNIVERSAL_IMPL_TRAIT = CompilerFeature("universal_impl_trait", ACCEPTED, "1.26.0")
// Allows `impl Trait` in function return types.
val CONSERVATIVE_IMPL_TRAIT = CompilerFeature("conservative_impl_trait", ACCEPTED, "1.26.0")
// Allows using the `u128` and `i128` types.
val I128_TYPE = CompilerFeature("i128_type", ACCEPTED, "1.26.0")
// Allows default match binding modes (RFC 2005).
val MATCH_DEFAULT_BINDINGS = CompilerFeature("match_default_bindings", ACCEPTED, "1.26.0")
// Allows `'_` placeholder lifetimes.
val UNDERSCORE_LIFETIMES = CompilerFeature("underscore_lifetimes", ACCEPTED, "1.26.0")
// Allows attributes on lifetime/type formal parameters in generics (RFC 1327).
val GENERIC_PARAM_ATTRS = CompilerFeature("generic_param_attrs", ACCEPTED, "1.27.0")
// Allows `cfg(target_feature = "...")`.
val CFG_TARGET_FEATURE = CompilerFeature("cfg_target_feature", ACCEPTED, "1.27.0")
// Allows `#[target_feature(...)]`.
val TARGET_FEATURE = CompilerFeature("target_feature", ACCEPTED, "1.27.0")
// Allows using `dyn Trait` as a syntax for trait objects.
val DYN_TRAIT = CompilerFeature("dyn_trait", ACCEPTED, "1.27.0")
// Allows `#[must_use]` on functions, and introduces must-use operators (RFC 1940).
val FN_MUST_USE = CompilerFeature("fn_must_use", ACCEPTED, "1.27.0")
// Allows use of the `:lifetime` macro fragment specifier.
val MACRO_LIFETIME_MATCHER = CompilerFeature("macro_lifetime_matcher", ACCEPTED, "1.27.0")
// Allows `#[test]` functions where the return type implements `Termination` (RFC 1937).
val TERMINATION_TRAIT_TEST = CompilerFeature("termination_trait_test", ACCEPTED, "1.27.0")
// Allows the `#[global_allocator]` attribute.
val GLOBAL_ALLOCATOR = CompilerFeature("global_allocator", ACCEPTED, "1.28.0")
// Allows `#[repr(transparent)]` attribute on newtype structs.
val REPR_TRANSPARENT = CompilerFeature("repr_transparent", ACCEPTED, "1.28.0")
// Allows procedural macros in `proc-macro` crates.
val PROC_MACRO = CompilerFeature("proc_macro", ACCEPTED, "1.29.0")
// Allows `foo.rs` as an alternative to `foo/mod.rs`.
val NON_MODRS_MODS = CompilerFeature("non_modrs_mods", ACCEPTED, "1.30.0")
// Allows use of the `:vis` macro fragment specifier
val MACRO_VIS_MATCHER = CompilerFeature("macro_vis_matcher", ACCEPTED, "1.30.0")
// Allows importing and reexporting macros with `use`,
// enables macro modularization in general.
val USE_EXTERN_MACROS = CompilerFeature("use_extern_macros", ACCEPTED, "1.30.0")
// Allows keywords to be escaped for use as identifiers.
val RAW_IDENTIFIERS = CompilerFeature("raw_identifiers", ACCEPTED, "1.30.0")
// Allows attributes scoped to tools.
val TOOL_ATTRIBUTES = CompilerFeature("tool_attributes", ACCEPTED, "1.30.0")
// Allows multi-segment paths in attributes and derives.
val PROC_MACRO_PATH_INVOC = CompilerFeature("proc_macro_path_invoc", ACCEPTED, "1.30.0")
// Allows all literals in attribute lists and values of key-value pairs.
val ATTR_LITERALS = CompilerFeature("attr_literals", ACCEPTED, "1.30.0")
// Allows inferring outlives requirements (RFC 2093).
val INFER_OUTLIVES_REQUIREMENTS = CompilerFeature("infer_outlives_requirements", ACCEPTED, "1.30.0")
// Allows annotating functions conforming to `fn(&PanicInfo) -> !` with `#[panic_handler]`.
// This defines the behavior of panics.
val PANIC_HANDLER = CompilerFeature("panic_handler", ACCEPTED, "1.30.0")
// Allows `#[used]` to preserve symbols (see llvm.used).
val USED = CompilerFeature("used", ACCEPTED, "1.30.0")
// Allows `crate` in paths.
val CRATE_IN_PATHS = CompilerFeature("crate_in_paths", ACCEPTED, "1.30.0")
// Allows resolving absolute paths as paths from other crates.
val EXTERN_ABSOLUTE_PATHS = CompilerFeature("extern_absolute_paths", ACCEPTED, "1.30.0")
// Allows access to crate names passed via `--extern` through prelude.
val EXTERN_PRELUDE = CompilerFeature("extern_prelude", ACCEPTED, "1.30.0")
// Allows parentheses in patterns.
val PATTERN_PARENTHESES = CompilerFeature("pattern_parentheses", ACCEPTED, "1.31.0")
// Allows the definition of `const fn` functions.
val MIN_CONST_FN = CompilerFeature("min_const_fn", ACCEPTED, "1.31.0")
// Allows scoped lints.
val TOOL_LINTS = CompilerFeature("tool_lints", ACCEPTED, "1.31.0")
// Allows lifetime elision in `impl` headers. For example:
// + `impl<I:Iterator> Iterator for &mut Iterator`
// + `impl Debug for Foo<'_>`
val IMPL_HEADER_LIFETIME_ELISION = CompilerFeature("impl_header_lifetime_elision", ACCEPTED, "1.31.0")
// Allows `extern crate foo as bar;`. This puts `bar` into extern prelude.
val EXTERN_CRATE_ITEM_PRELUDE = CompilerFeature("extern_crate_item_prelude", ACCEPTED, "1.31.0")
// Allows use of the `:literal` macro fragment specifier (RFC 1576).
val MACRO_LITERAL_MATCHER = CompilerFeature("macro_literal_matcher", ACCEPTED, "1.32.0")
// Allows use of `?` as the Kleene "at most one" operator in macros.
val MACRO_AT_MOST_ONCE_REP = CompilerFeature("macro_at_most_once_rep", ACCEPTED, "1.32.0")
// Allows `Self` struct constructor (RFC 2302).
val SELF_STRUCT_CTOR = CompilerFeature("self_struct_ctor", ACCEPTED, "1.32.0")
// Allows `Self` in type definitions (RFC 2300).
val SELF_IN_TYPEDEFS = CompilerFeature("self_in_typedefs", ACCEPTED, "1.32.0")
// Allows `use x::y;` to search `x` in the current scope.
val UNIFORM_PATHS = CompilerFeature("uniform_paths", ACCEPTED, "1.32.0")
// Allows integer match exhaustiveness checking (RFC 2591).
val EXHAUSTIVE_INTEGER_PATTERNS = CompilerFeature("exhaustive_integer_patterns", ACCEPTED, "1.33.0")
// Allows `use path as _;` and `extern crate c as _;`.
val UNDERSCORE_IMPORTS = CompilerFeature("underscore_imports", ACCEPTED, "1.33.0")
// Allows `#[repr(packed(N))]` attribute on structs.
val REPR_PACKED = CompilerFeature("repr_packed", ACCEPTED, "1.33.0")
// Allows irrefutable patterns in `if let` and `while let` statements (RFC 2086).
val IRREFUTABLE_LET_PATTERNS = CompilerFeature("irrefutable_let_patterns", ACCEPTED, "1.33.0")
// Allows calling `const unsafe fn` inside `unsafe` blocks in `const fn` functions.
val MIN_CONST_UNSAFE_FN = CompilerFeature("min_const_unsafe_fn", ACCEPTED, "1.33.0")
// Allows let bindings, assignments and destructuring in `const` functions and constants.
// As long as control flow is not implemented in const eval, `&&` and `||` may not be used
// at the same time as let bindings.
val CONST_LET = CompilerFeature("const_let", ACCEPTED, "1.33.0")
// Allows `#[cfg_attr(predicate, multiple, attributes, here)]`.
val CFG_ATTR_MULTI = CompilerFeature("cfg_attr_multi", ACCEPTED, "1.33.0")
// Allows top level or-patterns (`p | q`) in `if let` and `while let`.
val IF_WHILE_OR_PATTERNS = CompilerFeature("if_while_or_patterns", ACCEPTED, "1.33.0")
// Allows `cfg(target_vendor = "...")`.
val CFG_TARGET_VENDOR = CompilerFeature("cfg_target_vendor", ACCEPTED, "1.33.0")
// Allows `extern crate self as foo;`.
// This puts local crate root into extern prelude under name `foo`.
val EXTERN_CRATE_SELF = CompilerFeature("extern_crate_self", ACCEPTED, "1.34.0")
// Allows arbitrary delimited token streams in non-macro attributes.
val UNRESTRICTED_ATTRIBUTE_TOKENS = CompilerFeature("unrestricted_attribute_tokens", ACCEPTED, "1.34.0")
// Allows paths to enum variants on type aliases including `Self`.
val TYPE_ALIAS_ENUM_VARIANTS = CompilerFeature("type_alias_enum_variants", ACCEPTED, "1.37.0")
// Allows using `#[repr(align(X))]` on enums with equivalent semantics
// to wrapping an enum in a wrapper struct with `#[repr(align(X))]`.
val REPR_ALIGN_ENUM = CompilerFeature("repr_align_enum", ACCEPTED, "1.37.0")
// Allows `const _: TYPE = VALUE`.
val UNDERSCORE_CONST_NAMES = CompilerFeature("underscore_const_names", ACCEPTED, "1.37.0")
// Allows free and inherent `async fn`s, `async` blocks, and `<expr>.await` expressions.
val ASYNC_AWAIT = CompilerFeature("async_await", ACCEPTED, "1.39.0")
// Allows mixing bind-by-move in patterns and references to those identifiers in guards.
val BIND_BY_MOVE_PATTERN_GUARDS = CompilerFeature("bind_by_move_pattern_guards", ACCEPTED, "1.39.0")
// Allows attributes in formal function parameters.
val PARAM_ATTRS = CompilerFeature("param_attrs", ACCEPTED, "1.39.0")
// Allows macro invocations in `extern {}` blocks.
val MACROS_IN_EXTERN = CompilerFeature("macros_in_extern", ACCEPTED, "1.40.0")
// Allows future-proofing enums/structs with the `#[non_exhaustive]` attribute (RFC 2008).
val NON_EXHAUSTIVE = CompilerFeature("non_exhaustive", ACCEPTED, "1.40.0")
// Allows calling constructor functions in `const fn`.
val CONST_CONSTRUCTOR = CompilerFeature("const_constructor", ACCEPTED, "1.40.0")
// Allows the use of `#[cfg(doctest)]`, set when rustdoc is collecting doctests.
val CFG_DOCTEST = CompilerFeature("cfg_doctest", ACCEPTED, "1.40.0")
// Allows relaxing the coherence rules such that
// `impl<T> ForeignTrait<LocalType> for ForeignType<T>` is permitted.
val RE_REBALANCE_COHERENCE = CompilerFeature("re_rebalance_coherence", ACCEPTED, "1.41.0")
// Allows #[repr(transparent)] on univariant enums (RFC 2645).
val TRANSPARENT_ENUMS = CompilerFeature("transparent_enums", ACCEPTED, "1.42.0")
// Allows using subslice patterns, `[a, .., b]` and `[a, xs @ .., b]`.
val SLICE_PATTERNS = CompilerFeature("slice_patterns", ACCEPTED, "1.42.0")
// Allows the use of `if` and `match` in constants.
val CONST_IF_MATCH = CompilerFeature("const_if_match", ACCEPTED, "1.46.0")
// Allows the use of `loop` and `while` in constants.
val CONST_LOOP = CompilerFeature("const_loop", ACCEPTED, "1.46.0")
// Allows `#[track_caller]` to be used which provides
// accurate caller location reporting during panic (RFC 2091).
val TRACK_CALLER = CompilerFeature("track_caller", ACCEPTED, "1.46.0")
