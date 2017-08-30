type FunType = Fn(f64) -> f64;

type FunTypeVoid = Fn();

type Sum = Box<A + Copy>;

type FunSum = Box<Fn(f64, f64) -> f64 + Send + Sync>;

type Shl = F<<i as B>::Q, T=bool>;
type Shr = Vec<Vec<f64>>;

type Path = io::Result<()>;

type AssocType = Box<Iterator<Item=(Idx, T)> + 'a>;

type GenericAssoc = Foo<T, U=i32>;

type Trailing1 = Box<TypeA<'static,>>;

type Trailing2<'a> = MyType<'a, (),>;

type TrailingCommaInFn = unsafe extern "system" fn(x: i32,) -> ();

fn foo<T>(xs: Vec<T>) -> impl Iterator<Item=impl FnOnce() -> T> + Clone {
    xs.into_iter().map(|x| || x)
}

struct S<F>
    where F: FnMut(&mut Self, &T) -> Result<(), <Self as Encoder>::Error>;

struct EmptyWhere where {}
