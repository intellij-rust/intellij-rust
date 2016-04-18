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

#[empty_attr()]
const t: i32 = 92;

fn attrs_on_statements() {
   #[cfg(test)]
   let x = 92;

   #[cfg(test)]
   loop {}

   #[cfg(test)]
   1 + 1;
}
