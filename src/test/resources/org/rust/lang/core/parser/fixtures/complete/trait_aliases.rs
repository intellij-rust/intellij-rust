trait DebugDefault = Debug + Default;

trait DebugDefaultWhere = Debug where Self: Default;

trait DebugDefaultSelf = where Self: Debug + Default;

trait IntoIntIterator = IntoIterator<Item=i32>;

trait LifetimeParametric<'a> = Iterator<Item=Cow<'a, [i32]>>;`

trait TypeParametric<T> = Iterator<Item=Cow<'static, [T]>>;
