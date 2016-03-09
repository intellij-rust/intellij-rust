#!/she-bang line
//inner attributes
#![crate_type = "lib"]
#![crate_name = "rary"]


mod empty {

}

fn main() {
    #![crate_type = "lib"]
}

enum E {
    #[cfg(test)] F(#[cfg(test)] i32)
}
