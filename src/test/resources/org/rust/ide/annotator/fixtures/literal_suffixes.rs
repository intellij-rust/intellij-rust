fn main() {
    let lit1 = <error descr="string literal with a suffix is invalid">"test"u8</error>;
    let lit2 = <error descr="char literal with a suffix is invalid">'c'u8</error>;
    let lit3 = <error descr="invalid suffix 'u8' for float literal; the suffix must be one of: 'f32', 'f64'">1.2u8</error>;
    let lit4 = <error descr="invalid suffix 'f34' for numeric literal; the suffix must be one of: 'u8', 'i8', 'u16', 'i16', 'u32', 'i32', 'u64', 'i64', 'isize', 'usize'">1f34</error>;
}
