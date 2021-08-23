extern fn baz() {}
unsafe extern fn foo() {}
unsafe extern "C" fn bar() {}
extern "R\x75st" fn extern_fn_with_escape_in_abi() {}
extern r"system" fn extern_fn_with_raw_abi() {}
extern 1 fn extern_fn_with_invalid_abi() {}
