fn expressions() {
   // expressions
   1 >> 1;
   x >>= 1;
   x >= 1;
   1 << 1;
   x <<= 1;
   x <= 1;

   // generics
   type T = Vec<Vec<_>>;
   let x: V<_>= ();
   let x: V<V<_>>= ();
   x.collect::<Vec<Vec<_>>>();
   type U = Vec<<i32 as F>::Q>;

   i < <u32>::max_value();
}
