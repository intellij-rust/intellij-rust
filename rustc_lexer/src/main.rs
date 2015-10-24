extern crate rustc_serialize;
extern crate syntex_syntax;

use std::io::prelude::*;
use std::env;
use std::fs::File;
use rustc_serialize::json;

use syntex_syntax::codemap::{CodeMap, Pos};
use syntex_syntax::diagnostic::{ColorConfig, Handler, SpanHandler};
use syntex_syntax::parse::lexer::{Reader, StringReader};
use syntex_syntax::parse::token::Token;

#[derive(RustcEncodable)]
struct TokAndSpan {
    tok: Token,
    from: usize,
    to: usize
}

fn main() {
    let file_name = env::args().nth(1).expect("Usage: <filename>");
    let mut f = File::open(file_name.clone()).unwrap();
    let mut s = String::new();
    f.read_to_string(&mut s).unwrap();

    let h = Handler::new(ColorConfig::Auto, None, false);
    let sh = SpanHandler::new(h, CodeMap::new());
    let fm = sh.cm.new_filemap(file_name, s);
    let mut lexer = StringReader::new(&sh, fm);
    loop {
        let t = lexer.next_token();
        let tok_span = TokAndSpan {
            tok: t.tok.clone(),
            from: t.sp.lo.to_usize(),
            to: t.sp.hi.to_usize()
        };
        if t.tok == Token::Eof {
            break;
        }
        println!("{}", json::encode(&tok_span).unwrap());
    }
}