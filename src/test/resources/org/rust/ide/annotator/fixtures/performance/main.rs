use std::io::prelude::*;
use std::io;
use std::fs::File;
use std::collections::HashMap;

#[derive(Clone, PartialEq, Eq, Hash)]
pub struct Spam;

mod foo;
mod traits;

fn main() {
    use self::traits::Duplicator;

    let mut m = HashMap::new();

    let spam = Spam::create();
    spam.consume();

    let (s1, s2) = Spam::create().duplicate();
    m.insert(s1, s2);

    let eggs = Eggs::from(Spam);
    println!("{:?}", Some(eggs).unwrap());

    ::std::thread::spawn(|| {
        let _ = Vec::<f64>::new();
    }).join().unwrap();

    let mut f: &File = &File::open("foo.txt").unwrap();
    let mut buffer = String::new();
    f.read_to_string(&mut buffer);
}


#[derive(Debug)]
struct Eggs;

impl From<Spam> for Eggs {
    fn from(_spam: Spam) -> Eggs { Eggs }
}


mod inner {
    use traits::Duplicator;
    use Spam;

    impl Duplicator for Spam {
        fn duplicate(self) -> (Spam, Spam) {
            (self.clone(), self.clone())
        }
    }
}

