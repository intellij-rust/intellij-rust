#![allow(dead_code)]




use std::fmt;



struct Rectangle {
    p1: Point,
    p2: Point,



    p4: Point,
}




enum Person {
    Skinny,
    Fat,



    Age(i32),



    Info {
        name: String,


        height: i32,
    }
}




impl fmt::Display for List {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        let List(ref vec) = *self;




        try!(write!(f, "["));




        // Iterate over `vec` in `v` while enumerating the iteration
        // count in `count`.




        for (count, v) in vec.iter().enumerate() {



            if count != 0 { try!(write!(f, ", ")); } /* bar */



            try!(write!(f, "{}", v));



        }




        // Close the opened bracket and return a fmt::Result value
        write!(f, "]")
    }
}
