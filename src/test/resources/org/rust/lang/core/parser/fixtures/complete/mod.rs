
mod arith {

    fn add(x: i32, y: i32) -> i32 {
      return x + y;
    }

    fn mul(x: i32, y: i32) -> i32 {
      x * y;
    }

    mod sub_mod_decl;
}


mod empty {

}

pub mod pub_mod {}
pub(crate) mod pub_crate_mod {}
unsafe mod unsafe_crate_mod {} // semantically invalid
pub unsafe mod pub_unsafe_crate_mod {} // semantically invalid

mod mod_decl;
pub mod pub_mod_decl;
pub(crate) mod pub_crate_mod_decl;
unsafe mod unsafe_mod_decl; // semantically invalid
pub unsafe mod pub_unsafe_mod_decl; // semantically invalid
