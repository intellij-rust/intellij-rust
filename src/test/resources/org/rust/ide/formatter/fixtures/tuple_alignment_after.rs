enum Foo {
    Bazlooooooooooong(i32, i32,
                      i32, i32,
                      i32, i32),
}

fn main() {
    let long_tuple: (u8, u16, u32, u64, u128,
                     i8, i16, i32, i64, i128,
                     f32, f64,
                     char, bool) = (1u8, 2u16, 3u32, 4u64, 5u128,
                                    -1i8, -2i16, -3i32, -4i64, -5i128,
                                    0.1f32, 0.2f64,
                                    'a', true);

    match 1 {
        FooSome(1234, true,
                5678, false,
                91011, "foobar") => 10
    }
}
