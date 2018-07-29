type FunType = Fn(f64) -> f64;

type FunTypeVoid = Fn();

type Sum = Box<A + Copy>;

type LifetimeSum = Box<'a + Copy>;

type HrtbSum = &(for<'a> Trait1 + for<'b> Trait2);

type FunSum = Box<Fn(f64, f64) -> f64 + Send + Sync>;
type FunSum2 = Box<Fn() -> () + Send>;
type FunRetDynTrait = Box<Fn() -> dyn Trait + Send>;

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

type DynTrait = dyn Trait;

struct S<F>
    where F: FnMut(&mut Self, &T) -> Result<(), <Self as Encoder>::Error>;

struct EmptyWhere where {}

fn bar() -> foo!() { let a: foo!() = 0 as foo!(); a }
