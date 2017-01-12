#![allow(dead_code)]


use std::cmp::{max, min};


struct Rectangle {
    p1: (i32, i32),

    p2: (i32, i32),
}


impl Rectangle {
    fn dimensions(&self) -> (i32, i32) {
        let (x1, y1) = self.p1;
        let (x2, y2) = self.p2;


        ((x1 - x2).abs(), (y1 - y2).abs())
    }


    fn area(&self) -> i32 {
        let (a, b) = self.dimensions();
        a * b
    }
}
