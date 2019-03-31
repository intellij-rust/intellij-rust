/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("unused")

package org.rust.lang.core

import org.rust.lang.core.FeatureState.ACCEPTED
import org.rust.lang.core.FeatureState.ACTIVE

val ASM = CompilerFeature("asm", ACTIVE, "1.0.0")
val CONCAT_IDENTS = CompilerFeature("concat_idents", ACTIVE, "1.0.0")
val LINK_ARGS = CompilerFeature("link_args", ACTIVE, "1.0.0")
val LOG_SYNTAX = CompilerFeature("log_syntax", ACTIVE, "1.0.0")
val NON_ASCII_IDENTS = CompilerFeature("non_ascii_idents", ACTIVE, "1.0.0")
val PLUGIN_REGISTRAR = CompilerFeature("plugin_registrar", ACTIVE, "1.0.0")
val THREAD_LOCAL = CompilerFeature("thread_local", ACTIVE, "1.0.0")
val TRACE_MACROS = CompilerFeature("trace_macros", ACTIVE, "1.0.0")
// rustc internal, for now
val INTRINSICS = CompilerFeature("intrinsics", ACTIVE, "1.0.0")
val LANG_ITEMS = CompilerFeature("lang_items", ACTIVE, "1.0.0")
val FORMAT_ARGS_NL = CompilerFeature("format_args_nl", ACTIVE, "1.29.0")
val LINK_LLVM_INTRINSICS = CompilerFeature("link_llvm_intrinsics", ACTIVE, "1.0.0")
val LINKAGE = CompilerFeature("linkage", ACTIVE, "1.0.0")
// rustc internal
val RUSTC_DIAGNOSTIC_MACROS = CompilerFeature("rustc_diagnostic_macros", ACTIVE, "1.0.0")
val RUSTC_CONST_UNSTABLE = CompilerFeature("rustc_const_unstable", ACTIVE, "1.0.0")
val BOX_SYNTAX = CompilerFeature("box_syntax", ACTIVE, "1.0.0")
val UNBOXED_CLOSURES = CompilerFeature("unboxed_closures", ACTIVE, "1.0.0")
val FUNDAMENTAL = CompilerFeature("fundamental", ACTIVE, "1.0.0")
val MAIN = CompilerFeature("main", ACTIVE, "1.0.0")
val NEEDS_ALLOCATOR = CompilerFeature("needs_allocator", ACTIVE, "1.4.0")
val ON_UNIMPLEMENTED = CompilerFeature("on_unimplemented", ACTIVE, "1.0.0")
val PLUGIN = CompilerFeature("plugin", ACTIVE, "1.0.0")
val SIMD_FFI = CompilerFeature("simd_ffi", ACTIVE, "1.0.0")
val START = CompilerFeature("start", ACTIVE, "1.0.0")
val STRUCTURAL_MATCH = CompilerFeature("structural_match", ACTIVE, "1.8.0")
val PANIC_RUNTIME = CompilerFeature("panic_runtime", ACTIVE, "1.10.0")
val NEEDS_PANIC_RUNTIME = CompilerFeature("needs_panic_runtime", ACTIVE, "1.10.0")
// Features specific to OIBIT (auto traits)
val OPTIN_BUILTIN_TRAITS = CompilerFeature("optin_builtin_traits", ACTIVE, "1.0.0")
// Allows `#[staged_api]`.
//
// rustc internal
val STAGED_API = CompilerFeature("staged_api", ACTIVE, "1.0.0")
// Allows `#![no_core]`.
val NO_CORE = CompilerFeature("no_core", ACTIVE, "1.3.0")
// Allows the use of `box` in patterns (RFC 469).
val BOX_PATTERNS = CompilerFeature("box_patterns", ACTIVE, "1.0.0")
// Allows the use of the `unsafe_destructor_blind_to_params` attribute (RFC 1238).
val DROPCK_PARAMETRICITY = CompilerFeature("dropck_parametricity", ACTIVE, "1.3.0")
// Allows using the `may_dangle` attribute (RFC 1327).
val DROPCK_EYEPATCH = CompilerFeature("dropck_eyepatch", ACTIVE, "1.10.0")
// Allows the use of custom attributes (RFC 572).
val CUSTOM_ATTRIBUTE = CompilerFeature("custom_attribute", ACTIVE, "1.0.0")
// Allows the use of `rustc_*` attributes (RFC 572).
val RUSTC_ATTRS = CompilerFeature("rustc_attrs", ACTIVE, "1.0.0")
// Allows the use of non lexical lifetimes (RFC 2094).
val NLL = CompilerFeature("nll", ACTIVE, "1.0.0")
// Allows the use of `#[allow_internal_unstable]`. This is an
// attribute on `macro_rules!` and can't use the attribute handling
// below (it has to be checked before expansion possibly makes
// macros disappear).
//
// rustc internal
val ALLOW_INTERNAL_UNSTABLE = CompilerFeature("allow_internal_unstable", ACTIVE, "1.0.0")
// Allows the use of `#[allow_internal_unsafe]`. This is an
// attribute on `macro_rules!` and can't use the attribute handling
// below (it has to be checked before expansion possibly makes
// macros disappear).
//
// rustc internal
val ALLOW_INTERNAL_UNSAFE = CompilerFeature("allow_internal_unsafe", ACTIVE, "1.0.0")
// Allows the use of slice patterns (issue #23121).
val SLICE_PATTERNS = CompilerFeature("slice_patterns", ACTIVE, "1.0.0")
// Allows the definition of `const` functions with some advanced features.
val CONST_FN = CompilerFeature("const_fn", ACTIVE, "1.2.0")
// Allows accessing fields of unions inside `const` functions.
val CONST_FN_UNION = CompilerFeature("const_fn_union", ACTIVE, "1.27.0")
// Allows casting raw pointers to `usize` during const eval.
val CONST_RAW_PTR_TO_USIZE_CAST = CompilerFeature("const_raw_ptr_to_usize_cast", ACTIVE, "1.27.0")
// Allows dereferencing raw pointers during const eval.
val CONST_RAW_PTR_DEREF = CompilerFeature("const_raw_ptr_deref", ACTIVE, "1.27.0")
// Allows reinterpretation of the bits of a value of one type as another type during const eval.
val CONST_TRANSMUTE = CompilerFeature("const_transmute", ACTIVE, "1.29.0")
// Allows comparing raw pointers during const eval.
val CONST_COMPARE_RAW_POINTERS = CompilerFeature("const_compare_raw_pointers", ACTIVE, "1.27.0")
// Allows panicking during const eval (producing compile-time errors).
val CONST_PANIC = CompilerFeature("const_panic", ACTIVE, "1.30.0")
// Allows using `#[prelude_import]` on glob `use` items.
//
// rustc internal
val PRELUDE_IMPORT = CompilerFeature("prelude_import", ACTIVE, "1.2.0")
// Allows default type parameters to influence type inference.
val DEFAULT_TYPE_PARAMETER_FALLBACK = CompilerFeature("default_type_parameter_fallback", ACTIVE, "1.3.0")
// Allows associated type defaults.
val ASSOCIATED_TYPE_DEFAULTS = CompilerFeature("associated_type_defaults", ACTIVE, "1.2.0")
// Allows `repr(simd)` and importing the various simd intrinsics.
val REPR_SIMD = CompilerFeature("repr_simd", ACTIVE, "1.4.0")
// Allows `extern "platform-intrinsic" { ... }`.
val PLATFORM_INTRINSICS = CompilerFeature("platform_intrinsics", ACTIVE, "1.4.0")
// Allows `#[unwind(..)]`.
//
// Permits specifying whether a function should permit unwinding or abort on unwind.
val UNWIND_ATTRIBUTES = CompilerFeature("unwind_attributes", ACTIVE, "1.4.0")
// Allows the use of `#[naked]` on functions.
val NAKED_FUNCTIONS = CompilerFeature("naked_functions", ACTIVE, "1.9.0")
// Allows `#[no_debug]`.
val NO_DEBUG = CompilerFeature("no_debug", ACTIVE, "1.5.0")
// Allows `#[omit_gdb_pretty_printer_section]`.
//
// rustc internal
val OMIT_GDB_PRETTY_PRINTER_SECTION = CompilerFeature("omit_gdb_pretty_printer_section", ACTIVE, "1.5.0")
// Allows attributes on expressions and non-item statements.
val STMT_EXPR_ATTRIBUTES = CompilerFeature("stmt_expr_attributes", ACTIVE, "1.6.0")
// Allows the use of type ascription in expressions.
val TYPE_ASCRIPTION = CompilerFeature("type_ascription", ACTIVE, "1.6.0")
// Allows `cfg(target_thread_local)`.
val CFG_TARGET_THREAD_LOCAL = CompilerFeature("cfg_target_thread_local", ACTIVE, "1.7.0")
// rustc internal
val ABI_VECTORCALL = CompilerFeature("abi_vectorcall", ACTIVE, "1.7.0")
// Allows `X..Y` patterns.
val EXCLUSIVE_RANGE_PATTERN = CompilerFeature("exclusive_range_pattern", ACTIVE, "1.11.0")
// impl specialization (RFC 1210)
val SPECIALIZATION = CompilerFeature("specialization", ACTIVE, "1.7.0")
// Allows `cfg(target_has_atomic = "...")`.
val CFG_TARGET_HAS_ATOMIC = CompilerFeature("cfg_target_has_atomic", ACTIVE, "1.9.0")
// The `!` type. Does not imply 'exhaustive_patterns' (below) any more.
val NEVER_TYPE = CompilerFeature("never_type", ACTIVE, "1.13.0")
// Allows exhaustive pattern matching on types that contain uninhabited types.
val EXHAUSTIVE_PATTERNS = CompilerFeature("exhaustive_patterns", ACTIVE, "1.13.0")
// Allows untagged unions `union U { ... }`.
val UNTAGGED_UNIONS = CompilerFeature("untagged_unions", ACTIVE, "1.13.0")
// Used to identify the `compiler_builtins` crate.
//
// rustc internal.
val COMPILER_BUILTINS = CompilerFeature("compiler_builtins", ACTIVE, "1.13.0")
// Allows `#[link(..., cfg(..))]`.
val LINK_CFG = CompilerFeature("link_cfg", ACTIVE, "1.14.0")
// Allows `extern "ptx-*" fn()`.
val ABI_PTX = CompilerFeature("abi_ptx", ACTIVE, "1.15.0")
// The `repr(i128)` annotation for enums.
val REPR128 = CompilerFeature("repr128", ACTIVE, "1.16.0")
// Allows the use of `#[ffi_returns_twice]` on foreign functions.
val FFI_RETURNS_TWICE = CompilerFeature("ffi_returns_twice", ACTIVE, "1.34.0")
// The `unadjusted` ABI; perma-unstable.
//
// rustc internal
val ABI_UNADJUSTED = CompilerFeature("abi_unadjusted", ACTIVE, "1.16.0")
// Declarative macros 2.0 (`macro`).
val DECL_MACRO = CompilerFeature("decl_macro", ACTIVE, "1.17.0")
// Allows `#[link(kind="static-nobundle"...)]`.
val STATIC_NOBUNDLE = CompilerFeature("static_nobundle", ACTIVE, "1.16.0")
// Allows `extern "msp430-interrupt" fn()`.
val ABI_MSP430_INTERRUPT = CompilerFeature("abi_msp430_interrupt", ACTIVE, "1.16.0")
// Used to identify crates that contain sanitizer runtimes.
//
// rustc internal
val SANITIZER_RUNTIME = CompilerFeature("sanitizer_runtime", ACTIVE, "1.17.0")
// Used to identify crates that contain the profiler runtime.
//
// rustc internal
val PROFILER_RUNTIME = CompilerFeature("profiler_runtime", ACTIVE, "1.18.0")
// Allows `extern "x86-interrupt" fn()`.
val ABI_X86_INTERRUPT = CompilerFeature("abi_x86_interrupt", ACTIVE, "1.17.0")
// Allows the `try {...}` expression.
val TRY_BLOCKS = CompilerFeature("try_blocks", ACTIVE, "1.29.0")
// Allows module-level inline assembly by way of `global_asm!()`.
val GLOBAL_ASM = CompilerFeature("global_asm", ACTIVE, "1.18.0")
// Allows overlapping impls of marker traits.
val OVERLAPPING_MARKER_TRAITS = CompilerFeature("overlapping_marker_traits", ACTIVE, "1.18.0")
// Trait attribute to allow overlapping impls.
val MARKER_TRAIT_ATTR = CompilerFeature("marker_trait_attr", ACTIVE, "1.30.0")
// rustc internal
val ABI_THISCALL = CompilerFeature("abi_thiscall", ACTIVE, "1.19.0")
// Allows a test to fail without failing the whole suite.
val ALLOW_FAIL = CompilerFeature("allow_fail", ACTIVE, "1.19.0")
// Allows unsized tuple coercion.
val UNSIZED_TUPLE_COERCION = CompilerFeature("unsized_tuple_coercion", ACTIVE, "1.20.0")
// Generators
val GENERATORS = CompilerFeature("generators", ACTIVE, "1.21.0")
// Trait aliases
val TRAIT_ALIAS = CompilerFeature("trait_alias", ACTIVE, "1.24.0")
// rustc internal
val ALLOCATOR_INTERNALS = CompilerFeature("allocator_internals", ACTIVE, "1.20.0")
// `#[doc(cfg(...))]`
val DOC_CFG = CompilerFeature("doc_cfg", ACTIVE, "1.21.0")
// `#[doc(masked)]`
val DOC_MASKED = CompilerFeature("doc_masked", ACTIVE, "1.21.0")
// `#[doc(spotlight)]`
val DOC_SPOTLIGHT = CompilerFeature("doc_spotlight", ACTIVE, "1.22.0")
// `#[doc(include = "some-file")]`
val EXTERNAL_DOC = CompilerFeature("external_doc", ACTIVE, "1.22.0")
// Future-proofing enums/structs with `#[non_exhaustive]` attribute (RFC 2008).
val NON_EXHAUSTIVE = CompilerFeature("non_exhaustive", ACTIVE, "1.22.0")
// Adds `crate` as visibility modifier, synonymous with `pub(crate)`.
val CRATE_VISIBILITY_MODIFIER = CompilerFeature("crate_visibility_modifier", ACTIVE, "1.23.0")
// extern types
val EXTERN_TYPES = CompilerFeature("extern_types", ACTIVE, "1.23.0")
// Allows trait methods with arbitrary self types.
val ARBITRARY_SELF_TYPES = CompilerFeature("arbitrary_self_types", ACTIVE, "1.23.0")
// In-band lifetime bindings (e.g., `fn foo(x: &'a u8) -> &'a u8`).
val IN_BAND_LIFETIMES = CompilerFeature("in_band_lifetimes", ACTIVE, "1.23.0")
// Generic associated types (RFC 1598)
val GENERIC_ASSOCIATED_TYPES = CompilerFeature("generic_associated_types", ACTIVE, "1.23.0")
// Infer static outlives requirements (RFC 2093).
val INFER_STATIC_OUTLIVES_REQUIREMENTS = CompilerFeature("infer_static_outlives_requirements", ACTIVE, "1.26.0")
// Allows macro invocations in `extern {}` blocks.
val MACROS_IN_EXTERN = CompilerFeature("macros_in_extern", ACTIVE, "1.27.0")
// `existential type`
val EXISTENTIAL_TYPE = CompilerFeature("existential_type", ACTIVE, "1.28.0")
// unstable `#[target_feature]` directives
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
// Allows macro invocations on modules expressions and statements and
// procedural macros to expand to non-items.
val PROC_MACRO_HYGIENE = CompilerFeature("proc_macro_hygiene", ACTIVE, "1.30.0")
// `#[doc(alias = "...")]`
val DOC_ALIAS = CompilerFeature("doc_alias", ACTIVE, "1.27.0")
// inconsistent bounds in where clauses
val TRIVIAL_BOUNDS = CompilerFeature("trivial_bounds", ACTIVE, "1.28.0")
// `'a: { break 'a; }`
val LABEL_BREAK_VALUE = CompilerFeature("label_break_value", ACTIVE, "1.28.0")
// Exhaustive pattern matching on `usize` and `isize`.
val PRECISE_POINTER_SIZE_MATCHING = CompilerFeature("precise_pointer_size_matching", ACTIVE, "1.32.0")
// `#[doc(keyword = "...")]`
val DOC_KEYWORD = CompilerFeature("doc_keyword", ACTIVE, "1.28.0")
// Allows async and await syntax.
val ASYNC_AWAIT = CompilerFeature("async_await", ACTIVE, "1.28.0")
// `#[alloc_error_handler]`
val ALLOC_ERROR_HANDLER = CompilerFeature("alloc_error_handler", ACTIVE, "1.29.0")
val ABI_AMDGPU_KERNEL = CompilerFeature("abi_amdgpu_kernel", ACTIVE, "1.29.0")
// Added for testing E0705; perma-unstable.
val TEST_2018_FEATURE = CompilerFeature("test_2018_feature", ACTIVE, "1.31.0")
// Allows unsized rvalues at arguments and parameters.
val UNSIZED_LOCALS = CompilerFeature("unsized_locals", ACTIVE, "1.30.0")
// `#![test_runner]`
// `#[test_case]`
val CUSTOM_TEST_FRAMEWORKS = CompilerFeature("custom_test_frameworks", ACTIVE, "1.30.0")
// non-builtin attributes in inner attribute position
val CUSTOM_INNER_ATTRIBUTES = CompilerFeature("custom_inner_attributes", ACTIVE, "1.30.0")
// Allow mixing of bind-by-move in patterns and references to
// those identifiers in guards, *if* we are using MIR-borrowck
// (aka NLL). Essentially this means you need to be using the
// 2018 edition or later.
val BIND_BY_MOVE_PATTERN_GUARDS = CompilerFeature("bind_by_move_pattern_guards", ACTIVE, "1.30.0")
// Allows `impl Trait` in bindings (`let`, `const`, `static`).
val IMPL_TRAIT_IN_BINDINGS = CompilerFeature("impl_trait_in_bindings", ACTIVE, "1.30.0")
// Allows `const _: TYPE = VALUE`.
val UNDERSCORE_CONST_NAMES = CompilerFeature("underscore_const_names", ACTIVE, "1.31.0")
// Adds `reason` and `expect` lint attributes.
val LINT_REASONS = CompilerFeature("lint_reasons", ACTIVE, "1.31.0")
// Allows paths to enum variants on type aliases.
val TYPE_ALIAS_ENUM_VARIANTS = CompilerFeature("type_alias_enum_variants", ACTIVE, "1.31.0")
// Re-Rebalance coherence
val RE_REBALANCE_COHERENCE = CompilerFeature("re_rebalance_coherence", ACTIVE, "1.32.0")
// Const generic types.
val CONST_GENERICS = CompilerFeature("const_generics", ACTIVE, "1.34.0")
// #[optimize(X)]
val OPTIMIZE_ATTRIBUTE = CompilerFeature("optimize_attribute", ACTIVE, "1.34.0")
// #[repr(align(X))] on enums
val REPR_ALIGN_ENUM = CompilerFeature("repr_align_enum", ACTIVE, "1.34.0")
// Allows the use of C-variadics
val C_VARIADIC = CompilerFeature("c_variadic", ACTIVE, "1.34.0")

val ASSOCIATED_TYPES = CompilerFeature("associated_types", ACCEPTED, "1.0.0")
// Allows overloading augmented assignment operations like `a += b`.
val AUGMENTED_ASSIGNMENTS = CompilerFeature("augmented_assignments", ACCEPTED, "1.8.0")
// Allows empty structs and enum variants with braces.
val BRACED_EMPTY_STRUCTS = CompilerFeature("braced_empty_structs", ACCEPTED, "1.8.0")
// Allows indexing into constant arrays.
val CONST_INDEXING = CompilerFeature("const_indexing", ACCEPTED, "1.26.0")
val DEFAULT_TYPE_PARAMS = CompilerFeature("default_type_params", ACCEPTED, "1.0.0")
val GLOBS = CompilerFeature("globs", ACCEPTED, "1.0.0")
val IF_LET = CompilerFeature("if_let", ACCEPTED, "1.0.0")
// A temporary feature gate used to enable parser extensions needed
// to bootstrap fix for #5723.
val ISSUE_5723_BOOTSTRAP = CompilerFeature("issue_5723_bootstrap", ACCEPTED, "1.0.0")
val MACRO_RULES = CompilerFeature("macro_rules", ACCEPTED, "1.0.0")
// Allows using `#![no_std]`.
val NO_STD = CompilerFeature("no_std", ACCEPTED, "1.6.0")
val SLICING_SYNTAX = CompilerFeature("slicing_syntax", ACCEPTED, "1.0.0")
val STRUCT_VARIANT = CompilerFeature("struct_variant", ACCEPTED, "1.0.0")
// These are used to test this portion of the compiler, they don't actually
// mean anything.
val TEST_ACCEPTED_FEATURE = CompilerFeature("test_accepted_feature", ACCEPTED, "1.0.0")
val TUPLE_INDEXING = CompilerFeature("tuple_indexing", ACCEPTED, "1.0.0")
// Allows macros to appear in the type position.
val TYPE_MACROS = CompilerFeature("type_macros", ACCEPTED, "1.13.0")
val WHILE_LET = CompilerFeature("while_let", ACCEPTED, "1.0.0")
// Allows `#[deprecated]` attribute.
val DEPRECATED = CompilerFeature("deprecated", ACCEPTED, "1.9.0")
// `expr?`
val QUESTION_MARK = CompilerFeature("question_mark", ACCEPTED, "1.13.0")
// Allows `..` in tuple (struct) patterns.
val DOTDOT_IN_TUPLE_PATTERNS = CompilerFeature("dotdot_in_tuple_patterns", ACCEPTED, "1.14.0")
val ITEM_LIKE_IMPORTS = CompilerFeature("item_like_imports", ACCEPTED, "1.15.0")
// Allows using `Self` and associated types in struct expressions and patterns.
val MORE_STRUCT_ALIASES = CompilerFeature("more_struct_aliases", ACCEPTED, "1.16.0")
// elide `'static` lifetimes in `static`s and `const`s.
val STATIC_IN_CONST = CompilerFeature("static_in_const", ACCEPTED, "1.17.0")
// Allows field shorthands (`x` meaning `x: x`) in struct literal expressions.
val FIELD_INIT_SHORTHAND = CompilerFeature("field_init_shorthand", ACCEPTED, "1.17.0")
// Allows the definition recursive static items.
val STATIC_RECURSION = CompilerFeature("static_recursion", ACCEPTED, "1.17.0")
// `pub(restricted)` visibilities (RFC 1422)
val PUB_RESTRICTED = CompilerFeature("pub_restricted", ACCEPTED, "1.18.0")
// `#![windows_subsystem]`
val WINDOWS_SUBSYSTEM = CompilerFeature("windows_subsystem", ACCEPTED, "1.18.0")
// Allows `break {expr}` with a value inside `loop`s.
val LOOP_BREAK_VALUE = CompilerFeature("loop_break_value", ACCEPTED, "1.19.0")
// Permits numeric fields in struct expressions and patterns.
val RELAXED_ADTS = CompilerFeature("relaxed_adts", ACCEPTED, "1.19.0")
// Coerces non capturing closures to function pointers.
val CLOSURE_TO_FN_COERCION = CompilerFeature("closure_to_fn_coercion", ACCEPTED, "1.19.0")
// Allows attributes on struct literal fields.
val STRUCT_FIELD_ATTRIBUTES = CompilerFeature("struct_field_attributes", ACCEPTED, "1.20.0")
// Allows the definition of associated constants in `trait` or `impl` blocks.
val ASSOCIATED_CONSTS = CompilerFeature("associated_consts", ACCEPTED, "1.20.0")
// Usage of the `compile_error!` macro.
val COMPILE_ERROR = CompilerFeature("compile_error", ACCEPTED, "1.20.0")
// See rust-lang/rfcs#1414. Allows code like `let x: &'static u32 = &42` to work.
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
// Nested groups in `use` (RFC 2128)
val USE_NESTED_GROUPS = CompilerFeature("use_nested_groups", ACCEPTED, "1.25.0")
// `a..=b` and `..=b`
val INCLUSIVE_RANGE_SYNTAX = CompilerFeature("inclusive_range_syntax", ACCEPTED, "1.26.0")
// Allows `..=` in patterns (RFC 1192).
val DOTDOTEQ_IN_PATTERNS = CompilerFeature("dotdoteq_in_patterns", ACCEPTED, "1.26.0")
// Termination trait in main (RFC 1937)
val TERMINATION_TRAIT = CompilerFeature("termination_trait", ACCEPTED, "1.26.0")
// `Copy`/`Clone` closures (RFC 2132).
val CLONE_CLOSURES = CompilerFeature("clone_closures", ACCEPTED, "1.26.0")
val COPY_CLOSURES = CompilerFeature("copy_closures", ACCEPTED, "1.26.0")
// Allows `impl Trait` in function arguments.
val UNIVERSAL_IMPL_TRAIT = CompilerFeature("universal_impl_trait", ACCEPTED, "1.26.0")
// Allows `impl Trait` in function return types.
val CONSERVATIVE_IMPL_TRAIT = CompilerFeature("conservative_impl_trait", ACCEPTED, "1.26.0")
// The `i128` type
val I128_TYPE = CompilerFeature("i128_type", ACCEPTED, "1.26.0")
// Default match binding modes (RFC 2005)
val MATCH_DEFAULT_BINDINGS = CompilerFeature("match_default_bindings", ACCEPTED, "1.26.0")
// Allows `'_` placeholder lifetimes.
val UNDERSCORE_LIFETIMES = CompilerFeature("underscore_lifetimes", ACCEPTED, "1.26.0")
// Allows attributes on lifetime/type formal parameters in generics (RFC 1327).
val GENERIC_PARAM_ATTRS = CompilerFeature("generic_param_attrs", ACCEPTED, "1.27.0")
// Allows `cfg(target_feature = "...")`.
val CFG_TARGET_FEATURE = CompilerFeature("cfg_target_feature", ACCEPTED, "1.27.0")
// Allows `#[target_feature(...)]`.
val TARGET_FEATURE = CompilerFeature("target_feature", ACCEPTED, "1.27.0")
// Trait object syntax with `dyn` prefix
val DYN_TRAIT = CompilerFeature("dyn_trait", ACCEPTED, "1.27.0")
// Allows `#[must_use]` on functions, and introduces must-use operators (RFC 1940).
val FN_MUST_USE = CompilerFeature("fn_must_use", ACCEPTED, "1.27.0")
// Allows use of the `:lifetime` macro fragment specifier.
val MACRO_LIFETIME_MATCHER = CompilerFeature("macro_lifetime_matcher", ACCEPTED, "1.27.0")
// Termination trait in tests (RFC 1937)
val TERMINATION_TRAIT_TEST = CompilerFeature("termination_trait_test", ACCEPTED, "1.27.0")
// The `#[global_allocator]` attribute
val GLOBAL_ALLOCATOR = CompilerFeature("global_allocator", ACCEPTED, "1.28.0")
// Allows `#[repr(transparent)]` attribute on newtype structs.
val REPR_TRANSPARENT = CompilerFeature("repr_transparent", ACCEPTED, "1.28.0")
// Procedural macros in `proc-macro` crates
val PROC_MACRO = CompilerFeature("proc_macro", ACCEPTED, "1.29.0")
// `foo.rs` as an alternative to `foo/mod.rs`
val NON_MODRS_MODS = CompilerFeature("non_modrs_mods", ACCEPTED, "1.30.0")
// Allows use of the `:vis` macro fragment specifier
val MACRO_VIS_MATCHER = CompilerFeature("macro_vis_matcher", ACCEPTED, "1.30.0")
// Allows importing and reexporting macros with `use`,
// enables macro modularization in general.
val USE_EXTERN_MACROS = CompilerFeature("use_extern_macros", ACCEPTED, "1.30.0")
// Allows keywords to be escaped for use as identifiers.
val RAW_IDENTIFIERS = CompilerFeature("raw_identifiers", ACCEPTED, "1.30.0")
// Attributes scoped to tools.
val TOOL_ATTRIBUTES = CompilerFeature("tool_attributes", ACCEPTED, "1.30.0")
// Allows multi-segment paths in attributes and derives.
val PROC_MACRO_PATH_INVOC = CompilerFeature("proc_macro_path_invoc", ACCEPTED, "1.30.0")
// Allows all literals in attribute lists and values of key-value pairs.
val ATTR_LITERALS = CompilerFeature("attr_literals", ACCEPTED, "1.30.0")
// Infer outlives requirements (RFC 2093).
val INFER_OUTLIVES_REQUIREMENTS = CompilerFeature("infer_outlives_requirements", ACCEPTED, "1.30.0")
val PANIC_HANDLER = CompilerFeature("panic_handler", ACCEPTED, "1.30.0")
// Used to preserve symbols (see llvm.used).
val USED = CompilerFeature("used", ACCEPTED, "1.30.0")
// `crate` in paths
val CRATE_IN_PATHS = CompilerFeature("crate_in_paths", ACCEPTED, "1.30.0")
// Resolve absolute paths as paths from other crates.
val EXTERN_ABSOLUTE_PATHS = CompilerFeature("extern_absolute_paths", ACCEPTED, "1.30.0")
// Access to crate names passed via `--extern` through prelude.
val EXTERN_PRELUDE = CompilerFeature("extern_prelude", ACCEPTED, "1.30.0")
// Parentheses in patterns
val PATTERN_PARENTHESES = CompilerFeature("pattern_parentheses", ACCEPTED, "1.31.0")
// Allows the definition of `const fn` functions.
val MIN_CONST_FN = CompilerFeature("min_const_fn", ACCEPTED, "1.31.0")
// Scoped lints
val TOOL_LINTS = CompilerFeature("tool_lints", ACCEPTED, "1.31.0")
// `impl<I:Iterator> Iterator for &mut Iterator`
// `impl Debug for Foo<'_>`
val IMPL_HEADER_LIFETIME_ELISION = CompilerFeature("impl_header_lifetime_elision", ACCEPTED, "1.31.0")
// `extern crate foo as bar;` puts `bar` into extern prelude.
val EXTERN_CRATE_ITEM_PRELUDE = CompilerFeature("extern_crate_item_prelude", ACCEPTED, "1.31.0")
// Allows use of the `:literal` macro fragment specifier (RFC 1576).
val MACRO_LITERAL_MATCHER = CompilerFeature("macro_literal_matcher", ACCEPTED, "1.32.0")
// Use `?` as the Kleene "at most one" operator.
val MACRO_AT_MOST_ONCE_REP = CompilerFeature("macro_at_most_once_rep", ACCEPTED, "1.32.0")
// `Self` struct constructor (RFC 2302)
val SELF_STRUCT_CTOR = CompilerFeature("self_struct_ctor", ACCEPTED, "1.32.0")
// `Self` in type definitions (RFC 2300)
val SELF_IN_TYPEDEFS = CompilerFeature("self_in_typedefs", ACCEPTED, "1.32.0")
// Allows `use x::y;` to search `x` in the current scope.
val UNIFORM_PATHS = CompilerFeature("uniform_paths", ACCEPTED, "1.32.0")
// Integer match exhaustiveness checking (RFC 2591)
val EXHAUSTIVE_INTEGER_PATTERNS = CompilerFeature("exhaustive_integer_patterns", ACCEPTED, "1.33.0")
// `use path as _;` and `extern crate c as _;`
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
// `#[cfg_attr(predicate, multiple, attributes, here)]`
val CFG_ATTR_MULTI = CompilerFeature("cfg_attr_multi", ACCEPTED, "1.33.0")
// Top level or-patterns (`p | q`) in `if let` and `while let`.
val IF_WHILE_OR_PATTERNS = CompilerFeature("if_while_or_patterns", ACCEPTED, "1.33.0")
// Allows `cfg(target_vendor = "...")`.
val CFG_TARGET_VENDOR = CompilerFeature("cfg_target_vendor", ACCEPTED, "1.33.0")
// `extern crate self as foo;` puts local crate root into extern prelude under name `foo`.
val EXTERN_CRATE_SELF = CompilerFeature("extern_crate_self", ACCEPTED, "1.34.0")
// support for arbitrary delimited token streams in non-macro attributes
val UNRESTRICTED_ATTRIBUTE_TOKENS = CompilerFeature("unrestricted_attribute_tokens", ACCEPTED, "1.34.0")
