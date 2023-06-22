extern crate proc_macro;

use proc_macro::{Delimiter, Group, Ident, Punct, Spacing, Span, TokenStream, TokenTree};

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

#[proc_macro]
pub fn function_like_do_println_braces(input: TokenStream) -> TokenStream {
    println!("{{}}");
    input
}

#[proc_macro]
pub fn function_like_do_println_text_in_braces(input: TokenStream) -> TokenStream {
    println!("{{hey there}}");
    input
}

#[proc_macro]
pub fn function_like_reverse_spans(item: TokenStream) -> TokenStream {
    let tts = item.into_iter().collect::<Vec<_>>();
    tts.iter().enumerate().map(|(i, tt)| {
        let mut tt2 = tt.clone();
        tt2.set_span(tts[tts.len() - 1 - i].span());
        tt2
    }).collect::<TokenStream>()
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

#[proc_macro_derive(DeriveAsIsInNestedMod)]
pub fn derive_as_is_in_nested_mod(item: TokenStream) -> TokenStream {
    vec![
        Ident::new("mod", Span::call_site()).into(),
        Ident::new("inner", Span::call_site()).into(),
        TokenTree::Group(Group::new(Delimiter::Brace, item))
    ].into_iter().collect()
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

#[proc_macro_attribute]
pub fn attr_declare_struct_with_name(attr: TokenStream, _item: TokenStream) -> TokenStream {
    vec![
        Ident::new("struct", Span::call_site()).into(),
        attr.into_iter().next().unwrap(),
        TokenTree::Group(Group::new(Delimiter::Brace, TokenStream::new())),
    ].into_iter().collect()
}

/// The macro is hardcoded to be an "identity" macro in `HardcodedProcMacroProperties.kt`
#[proc_macro_attribute]
pub fn attr_hardcoded_not_a_macro(_attr: TokenStream, item: TokenStream) -> TokenStream {
   panic!("Must not be called")
}

/// The macro is hardcoded to be an "identity" macro in `HardcodedProcMacroProperties.kt`
#[proc_macro_attribute]
pub fn attr_hardcoded_as_is(_attr: TokenStream, item: TokenStream) -> TokenStream {
   item
}

/// Such a macro call
/// ```ignore
/// #[attr_add_to_fn_beginning(let a = 0;)]
/// fn main() {
///     let _ = a;
/// }
/// ```
/// will be expanded to
/// ```ignore
/// fn main() {
///     let a = 0;
///     let _ = a;
/// }
/// ```
#[proc_macro_attribute]
pub fn attr_add_to_fn_beginning(attr: TokenStream, item: TokenStream) -> TokenStream {
    item.into_iter().map(|tt| {
        match tt {
            TokenTree::Group(g) => {
                if g.delimiter() == Delimiter::Brace {
                    let mut dg = Group::new(
                        g.delimiter(),
                        attr.clone().into_iter().chain(g.stream().into_iter()).collect()
                    );
                    dg.set_span(g.span());
                    TokenTree::Group(dg)
                } else {
                    TokenTree::Group(g)
                }
            }
            TokenTree::Ident(..) => tt,
            TokenTree::Punct(..) | TokenTree::Literal(..) => tt,
        }
    }).collect()
}

#[proc_macro_attribute]
pub fn attr_as_is_discard_punct_spans(_attr: TokenStream, item: TokenStream) -> TokenStream {
    discard_punct_spans_stream(item)
}

fn discard_punct_spans_stream(ts: TokenStream) -> TokenStream {
    ts.into_iter().map(discard_punct_spans_tree).collect()
}

fn  discard_punct_spans_tree(tt: TokenTree) -> TokenTree {
    match tt {
        TokenTree::Group(g) => {
            let dg = Group::new(g.delimiter(), discard_punct_spans_stream(g.stream()));
            TokenTree::Group(dg)
        }
        TokenTree::Punct(p) => {
            TokenTree::Punct(Punct::new(p.as_char(), p.spacing()))
        }
        TokenTree::Ident(..) | TokenTree::Literal(..) => tt,
    }
}

/// Expands to `compile_error!("the error message")` attached to the first 3 tokens of the item
#[proc_macro_attribute]
pub fn attr_err_at_3_first_tokens(_attr: TokenStream, item: TokenStream) -> TokenStream {
    let mut iter = item.into_iter();
    let t1 = iter.next().unwrap();
    iter.next();
    let t2 = iter.next().unwrap();
    let span1 = t1.span();
    let span2 = t2.span();

    let mut msg = proc_macro::Literal::string("the error message");
    msg.set_span(span2.clone());

    let ident = Ident::new("compile_error", span1.clone());
    let mut punct = Punct::new('!', Spacing::Alone);
    punct.set_span(span1.clone());
    let mut group = Group::new(
        Delimiter::Brace,
        vec![TokenTree::Literal(msg)].into_iter().collect()
    );
    group.set_span(span2.clone());
    return vec![
        TokenTree::Ident(ident),
        TokenTree::Punct(punct),
        TokenTree::Group(group),
    ].into_iter().collect();
}
