#[macro_export]
#[doc(hidden)]
macro_rules! __diesel_column {
    ($($table:ident)::*, $column_name:ident -> $Type:ty) => {
        #[allow(non_camel_case_types, dead_code)]
        #[derive(Debug, Clone, Copy)]
        pub struct $column_name;

        impl $crate::expression::Expression for $column_name {
            type SqlType = $Type;
        }

        impl<DB> $crate::query_builder::QueryFragment<DB> for $column_name where
            DB: $crate::backend::Backend,
            <$($table)::* as QuerySource>::FromClause: QueryFragment<DB>,
        {
            fn to_sql(&self, out: &mut DB::QueryBuilder) -> $crate::query_builder::BuildQueryResult {
                try!($($table)::*.from_clause().to_sql(out));
                out.push_sql(".");
                out.push_identifier(stringify!($column_name))
            }

            fn collect_binds(&self, _out: &mut DB::BindCollector) -> $crate::result::QueryResult<()> {
                Ok(())
            }

            fn is_safe_to_cache_prepared(&self) -> bool {
                true
            }
        }

        impl_query_id!($column_name);

        impl SelectableExpression<$($table)::*> for $column_name {
        }

    }
}

#[macro_export]
macro_rules! table {
    // Put `use` statements at the end because macro_rules! cannot figure out
    // if `use` is an ident or not (hint: It's not)
    (
        use $($import:tt)::+; $($rest:tt)+
    ) => {
        table!($($rest)+ use $($import)::+;);
    };

    // Add the primary key if it's not present
    (
        $($table_name:ident).+ {$($body:tt)*}
        $($imports:tt)*
    ) => {
        table! {
            $($table_name).+ (id) {$($body)*} $($imports)*
        }
    };

    // Add the schema name if it's not present
    (
        $name:ident $(($($pk:ident),+))* {$($body:tt)*}
        $($imports:tt)*
    ) => {
        table! {
            public . $name $(($($pk),+))* {$($body)*} $($imports)*
        }
    };

    // Import `diesel::types::*` if no imports were given
    (
        $($table_name:ident).+ $(($($pk:ident),+))* {$($body:tt)*}
    ) => {
        table! {
            $($table_name).+ $(($($pk),+))* {$($body)*}
            use $crate::types::*;
        }
    };

    // Terminal with single-column pk
    (
        $schema_name:ident . $name:ident ($pk:ident) $body:tt
        $($imports:tt)+
    ) => {
        table_body! {
            $schema_name . $name ($pk) $body $($imports)+
        }
    };

    // Terminal with composite pk (add a trailing comma)
    (
        $schema_name:ident . $name:ident ($pk:ident, $($composite_pk:ident),+) $body:tt
        $($imports:tt)+
    ) => {
        table_body! {
            $schema_name . $name ($pk, $($composite_pk,)+) $body $($imports)+
        }
    };
}

#[macro_export]
#[doc(hidden)]
macro_rules! table_body {
    (
        $schema_name:ident . $name:ident ($pk:ident) {
            $($column_name:ident -> $Type:ty,)+
        }
        $(use $($import:tt)::+;)+
    ) => {
        table_body! {
            schema_name = $schema_name,
            table_name = $name,
            primary_key_ty = columns::$pk,
            primary_key_expr = columns::$pk,
            columns = [$($column_name -> $Type,)+],
            imports = ($($($import)::+),+),
        }
    };

    (
        $schema_name:ident . $name:ident ($($pk:ident,)+) {
            $($column_name:ident -> $Type:ty,)+
        }
        $(use $($import:tt)::+;)+
    ) => {
        table_body! {
            schema_name = $schema_name,
            table_name = $name,
            primary_key_ty = ($(columns::$pk,)+),
            primary_key_expr = ($(columns::$pk,)+),
            columns = [$($column_name -> $Type,)+],
            imports = ($($($import)::+),+),
        }
    };

    (
        schema_name = $schema_name:ident,
        table_name = $table_name:ident,
        primary_key_ty = $primary_key_ty:ty,
        primary_key_expr = $primary_key_expr:expr,
        columns = [$($column_name:ident -> $column_ty:ty,)+],
        imports = ($($($import:tt)::+),+),
    ) => {
        pub mod $table_name {
            #![allow(dead_code)]
            use $crate::{
                QuerySource,
                Table,
            };
            use $crate::associations::HasTable;
            $(use $($import)::+;)+
            __diesel_table_query_source_impl!(table, $schema_name, $table_name);

            impl_query_id!(table);

            pub mod columns {
                use super::table;
                use $crate::result::QueryResult;
                $(use $($import)::+;)+

                $(__diesel_column!(table, $column_name -> $column_ty);)+
            }
        }
    }
}

#[macro_export]
#[doc(hidden)]
macro_rules! __diesel_table_query_source_impl {
    ($table_struct:ident, public, $table_name:ident) => {
        impl QuerySource for $table_struct {
            type FromClause = Identifier<'static>;
            type DefaultSelection = <Self as Table>::AllColumns;

            fn from_clause(&self) -> Self::FromClause {
                Identifier(stringify!($table_name))
            }

            fn default_selection(&self) -> Self::DefaultSelection {
                Self::all_columns()
            }
        }
    };

    ($table_struct:ident, $schema_name:ident, $table_name:ident) => {
        impl QuerySource for $table_struct {
            type FromClause = $crate::query_builder::nodes::
                InfixNode<'static, Identifier<'static>, Identifier<'static>>;
            type DefaultSelection = <Self as Table>::AllColumns;

            fn from_clause(&self) -> Self::FromClause {
                $crate::query_builder::nodes::InfixNode::new(
                    Identifier(stringify!($schema_name)),
                    Identifier(stringify!($table_name)),
                    ".",
                )
            }

            fn default_selection(&self) -> Self::DefaultSelection {
                Self::all_columns()
            }
        }
    };
}

#[macro_export]
#[doc(hidden)]
macro_rules! joinable {
    ($child:ident -> $parent:ident ($source:ident)) => {
        joinable_inner!($child::table => $parent::table : ($child::$source = $parent::table));
        joinable_inner!($parent::table => $child::table : ($child::$source = $parent::table));
    }
}

#[macro_export]
#[doc(hidden)]
macro_rules! joinable_inner {
    ($left_table:path => $right_table:path : ($foreign_key:path = $parent_table:path)) => {
        joinable_inner!(
            left_table_ty = $left_table,
            right_table_ty = $right_table,
            right_table_expr = $right_table,
            foreign_key = $foreign_key,
            primary_key_ty = <$parent_table as $crate::query_source::Table>::PrimaryKey,
            primary_key_expr = $parent_table.primary_key(),
        );
    };

    (
        left_table_ty = $left_table_ty:ty,
        right_table_ty = $right_table_ty:ty,
        right_table_expr = $right_table_expr:expr,
        foreign_key = $foreign_key:path,
        primary_key_ty = $primary_key_ty:ty,
        primary_key_expr = $primary_key_expr:expr,
    ) => {
        impl<JoinType> $crate::JoinTo<$right_table_ty, JoinType> for $left_table_ty {
            type JoinClause = $crate::query_builder::nodes::Join<
                <$left_table_ty as $crate::QuerySource>::FromClause,
                <$right_table_ty as $crate::QuerySource>::FromClause,
                $crate::expression::helper_types::Eq<
                    $crate::expression::nullable::Nullable<$foreign_key>,
                    $crate::expression::nullable::Nullable<$primary_key_ty>,
                >,
                JoinType,
            >;
        }
    }
}

#[macro_export]
#[doc(hidden)]
macro_rules! join_through {
    ($parent:ident -> $through:ident -> $child:ident) => {
        impl<JoinType: Copy> $crate::JoinTo<$child::table, JoinType> for $parent::table {
            type JoinClause = <
                <$parent::table as $crate::JoinTo<$through::table, JoinType>>::JoinClause
                as $crate::query_builder::nodes::CombinedJoin<
                    <$through::table as $crate::JoinTo<$child::table, JoinType>>::JoinClause,
                >>::Output;

            fn join_clause(&self, join_type: JoinType) -> Self::JoinClause {
                use $crate::query_builder::nodes::CombinedJoin;
                let parent_to_through = $crate::JoinTo::<$through::table, JoinType>
                    ::join_clause(&$parent::table, join_type);
                let through_to_child = $crate::JoinTo::<$child::table, JoinType>
                    ::join_clause(&$through::table, join_type);
                parent_to_through.combine_with(through_to_child)
            }
        }
    }
}

#[macro_export]
macro_rules! debug_sql {
    ($query:expr) => {{
        use $crate::query_builder::{QueryFragment, QueryBuilder};
        use $crate::query_builder::debug::DebugQueryBuilder;
        let mut query_builder = DebugQueryBuilder::new();
        QueryFragment::<$crate::backend::Debug>::to_sql(&$query, &mut query_builder).unwrap();
        query_builder.finish()
    }};
}

#[macro_export]
macro_rules! print_sql {
    ($query:expr) => {
        println!("{}", &debug_sql!($query));
    };
}
