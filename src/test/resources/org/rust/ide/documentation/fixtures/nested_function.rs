mod a {
    mod b {
        mod c {
            pub fn double(x: i16) -> i32 {
                x * 2
            }
        }
    }

    fn main() {
        b::c::do<caret>uble(2);
    }
}
