#![allow(dead_code)]
#![cfg(test)]
#![foo]


#![bar]


use std::fmt; // Import the `fmt` module.


#[allow(dead_code)]
#[cfg(test)]
#[foo]
struct Rectangle {
    p1: Point,
    p2: Point,
    p3: Point,

    p4: Point,
    p5: Point,

    p6: Point,
    p7: Point
}


#[baz] /* x */
#[allow(dead_code)] /* a */
#[cfg(test)] /* b */
#[foo] /* c */
enum Person {
    // An `enum` may either be `unit-like`,
    Skinny,
    Fat,

    // like tuple structs,
    Height(i32),
    Weight(i32),

    // or like structures.
    Info {
        name: String,

        height: i32
    }
}


// Define a structure named `List` containing a `Vec`.
struct List(Vec<i32>);


/* hue */ impl fmt::Display for List {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        let List(ref vec) = *self;


        try!(write!(f, "["));


        // Iterate over `vec` in `v` while enumerating the iteration
        // count in `count`.


        for (count, v) in vec.iter().enumerate() {
            if count != 0 { try!(write!(f, ", ")); } /* bar */


            /* foo */ try!(write!(f, "{}", v));
        }


        // Close the opened bracket and return a fmt::Result value
        /* foo */ write!(f, "]")
    }
}


#[abra]
#[kada]
#[bra]
fn main() {
    let v = List(vec![1, 2, 3]);// foo
    println!("{}", v);   // bar


    /* moo */ println!("Hello World!");
}

fn many_stmts() {
    let a = {
        let inner = 3;
        (inner * inner)
    };
}

struct Foo {}

enum Moo {}

mod Bar {}

extern {}

fn closures() {
    let square = (|i: i32| i * i);

    let loooooooooooooong_name = |field| {
        if field.node.attrs.len() > 0 {
            field.node.attrs[0].span.lo
        } else {
            field.span.lo
        }
    };

    let empty = |arg| {};

    let test = || {
        do_something();
        do_something_else();
    };

    let bum = || {
        // comment
    };

    |arg1, arg2, _, _, arg3, arg4| {
        let temp = arg4 + arg3;
        arg2 * arg1 - temp
    }
}

fn reverse(pair: (i32, bool), a: i32,
           b: i32, c: i32,
           d: i32)
           -> (bool, i32) {}

fn boo(
    a: i32
) {}

fn misc() {
    let long_tuple = (1u8, 2u16, 3u32, 4u64, 5u128,
                      -1i8, -2i16, -3i32, -4i64, -5i128,
                      0.1f32, 0.2f64,
                      'a', true);

    let point: Point = Point { x: 0.3, y: 0.4 };

    let point: Point = Point {
        x: 0.3,

        y: 0.4
    };

    match p {
        Person::Engineer => println!("Is an engineer!"),
        Person::Scientist => println!("Is a scientist!"),

        Person::Height(i) => println!("Has a height of {}.", i),
        Person::Weight(i) => println!("Has a weight of {}.", i),

        Person::Info { name, height } => {
            println!("{} is {} tall!", name, height);
        }
    }
}

impl Foo {
    fn a() {}


    fn b() {}
}

mod moo {
    fn a() {}


    fn b() {}
}

