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

