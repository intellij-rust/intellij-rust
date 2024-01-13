/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion.lint

val RUSTC_LINTS: List<Lint> = listOf(
    Lint("future_incompatible", true),
    Lint("let_underscore", true),
    Lint("nonstandard_style", true),
    Lint("rust_2018_compatibility", true),
    Lint("rust_2018_idioms", true),
    Lint("rust_2021_compatibility", true),
    Lint("unused", true),
    Lint("warnings", true),
    Lint("absolute_paths_not_starting_with_crate", false),
    Lint("ambiguous_associated_items", false),
    Lint("ambiguous_glob_imports", false),
    Lint("ambiguous_glob_reexports", false),
    Lint("anonymous_parameters", false),
    Lint("arithmetic_overflow", false),
    Lint("array_into_iter", false),
    Lint("asm_sub_register", false),
    Lint("bad_asm_style", false),
    Lint("bare_trait_objects", false),
    Lint("bindings_with_variant_name", false),
    Lint("box_pointers", false),
    Lint("break_with_label_and_loop", false),
    Lint("byte_slice_in_packed_struct_with_derive", false),
    Lint("cenum_impl_drop_cast", false),
    Lint("clashing_extern_declarations", false),
    Lint("coherence_leak_check", false),
    Lint("coinductive_overlap_in_coherence", false),
    Lint("conflicting_repr_hints", false),
    Lint("confusable_idents", false),
    Lint("const_evaluatable_unchecked", false),
    Lint("const_item_mutation", false),
    Lint("dead_code", false),
    Lint("deprecated", false),
    Lint("deprecated_cfg_attr_crate_type_name", false),
    Lint("deprecated_in_future", false),
    Lint("deprecated_where_clause_location", false),
    Lint("deref_into_dyn_supertrait", false),
    Lint("deref_nullptr", false),
    Lint("drop_bounds", false),
    Lint("dropping_copy_types", false),
    Lint("dropping_references", false),
    Lint("duplicate_macro_attributes", false),
    Lint("dyn_drop", false),
    Lint("elided_lifetimes_in_associated_constant", false),
    Lint("elided_lifetimes_in_paths", false),
    Lint("ellipsis_inclusive_range_patterns", false),
    Lint("enum_intrinsics_non_enums", false),
    Lint("explicit_outlives_requirements", false),
    Lint("exported_private_dependencies", false),
    Lint("ffi_unwind_calls", false),
    Lint("for_loops_over_fallibles", false),
    Lint("forbidden_lint_groups", false),
    Lint("forgetting_copy_types", false),
    Lint("forgetting_references", false),
    Lint("function_item_references", false),
    Lint("fuzzy_provenance_casts", false),
    Lint("hidden_glob_reexports", false),
    Lint("ill_formed_attribute_input", false),
    Lint("illegal_floating_point_literal_pattern", false),
    Lint("implied_bounds_entailment", false),
    Lint("improper_ctypes", false),
    Lint("improper_ctypes_definitions", false),
    Lint("incomplete_features", false),
    Lint("incomplete_include", false),
    Lint("incorrect_fn_null_checks", false),
    Lint("indirect_structural_match", false),
    Lint("ineffective_unstable_trait_impl", false),
    Lint("inline_no_sanitize", false),
    Lint("internal_features", false),
    Lint("invalid_alignment", false),
    Lint("invalid_atomic_ordering", false),
    Lint("invalid_doc_attributes", false),
    Lint("invalid_from_utf8", false),
    Lint("invalid_from_utf8_unchecked", false),
    Lint("invalid_macro_export_arguments", false),
    Lint("invalid_nan_comparisons", false),
    Lint("invalid_reference_casting", false),
    Lint("invalid_type_param_default", false),
    Lint("invalid_value", false),
    Lint("irrefutable_let_patterns", false),
    Lint("keyword_idents", false),
    Lint("large_assignments", false),
    Lint("late_bound_lifetime_arguments", false),
    Lint("legacy_derive_helpers", false),
    Lint("let_underscore_drop", false),
    Lint("let_underscore_lock", false),
    Lint("long_running_const_eval", false),
    Lint("lossy_provenance_casts", false),
    Lint("macro_expanded_macro_exports_accessed_by_absolute_paths", false),
    Lint("macro_use_extern_crate", false),
    Lint("map_unit_fn", false),
    Lint("meta_variable_misuse", false),
    Lint("missing_abi", false),
    Lint("missing_copy_implementations", false),
    Lint("missing_debug_implementations", false),
    Lint("missing_docs", false),
    Lint("missing_fragment_specifier", false),
    Lint("mixed_script_confusables", false),
    Lint("multiple_supertrait_upcastable", false),
    Lint("must_not_suspend", false),
    Lint("mutable_transmutes", false),
    Lint("named_arguments_used_positionally", false),
    Lint("named_asm_labels", false),
    Lint("no_mangle_const_items", false),
    Lint("no_mangle_generic_items", false),
    Lint("non_ascii_idents", false),
    Lint("non_camel_case_types", false),
    Lint("non_exhaustive_omitted_patterns", false),
    Lint("non_fmt_panics", false),
    Lint("non_shorthand_field_patterns", false),
    Lint("non_snake_case", false),
    Lint("non_upper_case_globals", false),
    Lint("nontrivial_structural_match", false),
    Lint("noop_method_call", false),
    Lint("opaque_hidden_inferred_bound", false),
    Lint("order_dependent_trait_objects", false),
    Lint("overflowing_literals", false),
    Lint("overlapping_range_endpoints", false),
    Lint("path_statements", false),
    Lint("patterns_in_fns_without_body", false),
    Lint("pointer_structural_match", false),
    Lint("private_bounds", false),
    Lint("private_in_public", false),
    Lint("private_interfaces", false),
    Lint("proc_macro_back_compat", false),
    Lint("proc_macro_derive_resolution_fallback", false),
    Lint("pub_use_of_private_extern_crate", false),
    Lint("redundant_semicolons", false),
    Lint("renamed_and_removed_lints", false),
    Lint("repr_transparent_external_private_fields", false),
    Lint("rust_2021_incompatible_closure_captures", false),
    Lint("rust_2021_incompatible_or_patterns", false),
    Lint("rust_2021_prefixes_incompatible_syntax", false),
    Lint("rust_2021_prelude_collisions", false),
    Lint("semicolon_in_expressions_from_macros", false),
    Lint("single_use_lifetimes", false),
    Lint("soft_unstable", false),
    Lint("special_module_name", false),
    Lint("stable_features", false),
    Lint("suspicious_auto_trait_impls", false),
    Lint("suspicious_double_ref_op", false),
    Lint("temporary_cstring_as_ptr", false),
    Lint("test_unstable_lint", false),
    Lint("text_direction_codepoint_in_comment", false),
    Lint("text_direction_codepoint_in_literal", false),
    Lint("trivial_bounds", false),
    Lint("trivial_casts", false),
    Lint("trivial_numeric_casts", false),
    Lint("type_alias_bounds", false),
    Lint("tyvar_behind_raw_pointer", false),
    Lint("uncommon_codepoints", false),
    Lint("unconditional_panic", false),
    Lint("unconditional_recursion", false),
    Lint("undefined_naked_function_abi", false),
    Lint("undropped_manually_drops", false),
    Lint("unexpected_cfgs", false),
    Lint("unfulfilled_lint_expectations", false),
    Lint("ungated_async_fn_track_caller", false),
    Lint("uninhabited_static", false),
    Lint("unknown_crate_types", false),
    Lint("unknown_diagnostic_attributes", false),
    Lint("unknown_lints", false),
    Lint("unnameable_test_items", false),
    Lint("unnameable_types", false),
    Lint("unreachable_code", false),
    Lint("unreachable_patterns", false),
    Lint("unreachable_pub", false),
    Lint("unsafe_code", false),
    Lint("unsafe_op_in_unsafe_fn", false),
    Lint("unstable_features", false),
    Lint("unstable_name_collisions", false),
    Lint("unstable_syntax_pre_expansion", false),
    Lint("unsupported_calling_conventions", false),
    Lint("unused_allocation", false),
    Lint("unused_assignments", false),
    Lint("unused_associated_type_bounds", false),
    Lint("unused_attributes", false),
    Lint("unused_braces", false),
    Lint("unused_comparisons", false),
    Lint("unused_crate_dependencies", false),
    Lint("unused_doc_comments", false),
    Lint("unused_extern_crates", false),
    Lint("unused_features", false),
    Lint("unused_import_braces", false),
    Lint("unused_imports", false),
    Lint("unused_labels", false),
    Lint("unused_lifetimes", false),
    Lint("unused_macro_rules", false),
    Lint("unused_macros", false),
    Lint("unused_must_use", false),
    Lint("unused_mut", false),
    Lint("unused_parens", false),
    Lint("unused_qualifications", false),
    Lint("unused_results", false),
    Lint("unused_tuple_struct_fields", false),
    Lint("unused_unsafe", false),
    Lint("unused_variables", false),
    Lint("useless_deprecated", false),
    Lint("useless_ptr_null_checks", false),
    Lint("variant_size_differences", false),
    Lint("warnings", false),
    Lint("where_clauses_object_safety", false),
    Lint("while_true", false)
)
