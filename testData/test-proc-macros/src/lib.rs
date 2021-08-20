extern crate proc_macro;

use proc_macro::TokenStream;

#[proc_macro]
pub fn function_like_as_is(input: TokenStream) -> TokenStream {
    return input;
}

#[proc_macro]
pub fn function_like_read_env_var(input: TokenStream) -> TokenStream {
    use std::fmt::Write;
    let v = std::env::var("FOO_ENV_VAR").unwrap();
    let mut s = String::new();
    write!(&mut s, "\"{}\"", v);
    return s.parse().unwrap();
}

#[proc_macro]
pub fn function_like_do_println(input: TokenStream) -> TokenStream {
    println!("foobar");
    input
}

#[proc_macro]
pub fn function_like_do_eprintln(input: TokenStream) -> TokenStream {
    eprintln!("foobar");
    input
}

#[proc_macro]
pub fn function_like_do_panic(input: TokenStream) -> TokenStream {
    panic!("panic message");
}

#[proc_macro]
pub fn function_like_wait_100_seconds(input: TokenStream) -> TokenStream {
    std::thread::sleep(std::time::Duration::from_secs(100));
    return input;
}

#[proc_macro]
pub fn function_like_process_exit(input: TokenStream) -> TokenStream {
    std::process::exit(101)
}

#[proc_macro]
pub fn function_like_process_abort(input: TokenStream) -> TokenStream {
    std::process::abort()
}

// This also simulates the process killing during writing of an answer
#[proc_macro]
pub fn function_like_do_brace_println_and_process_exit(input: TokenStream) -> TokenStream {
    println!("{{");
    std::process::exit(101)
}

#[proc_macro_derive(DeriveImplForFoo)]
pub fn derive_impl_for_foo(_item: TokenStream) -> TokenStream {
   "impl Foo { fn foo(&self) -> Bar {} }".parse().unwrap()
}

#[proc_macro_derive(DeriveStructFooDeclaration)]
pub fn derive_struct_foo_declaration(_item: TokenStream) -> TokenStream {
   "struct Foo;".parse().unwrap()
}

#[proc_macro_derive(DeriveMacroFooThatExpandsToStructFoo)]
pub fn derive_macro_foo_that_expands_to_struct_foo(_item: TokenStream) -> TokenStream {
   "macro_rules! foo { () => { struct Foo; } }".parse().unwrap()
}

#[proc_macro_derive(DeriveMacroFooInvocation)]
pub fn derive_macro_foo_invocation(_item: TokenStream) -> TokenStream {
   "foo!{}".parse().unwrap()
}

#[proc_macro_derive(DeriveMacroBarInvocation)]
pub fn derive_macro_bar_invocation(_item: TokenStream) -> TokenStream {
   "bar!{}".parse().unwrap()
}

#[proc_macro]
pub fn function_like_generates_impl_for_foo(_input: TokenStream) -> TokenStream {
   "impl Foo { fn foo(&self) -> Bar {} }".parse().unwrap()
}

#[proc_macro_attribute]
pub fn attr_as_is(_attr: TokenStream, item: TokenStream) -> TokenStream {
   item
}

#[proc_macro_attribute]
pub fn attr_replace_with_attr(attr: TokenStream, item: TokenStream) -> TokenStream {
    attr
}

/// The macro is hardcoded to be an "identity" macro in `HardcodedProcMacroProperties.kt`
#[proc_macro_attribute]
pub fn attr_hardcoded_not_a_macro(_attr: TokenStream, item: TokenStream) -> TokenStream {
   panic!("Must not be called")
}
