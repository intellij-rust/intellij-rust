// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static org.rust.lang.core.psi.RustCompositeElementTypes.*;
import static org.rust.lang.core.parser.RustParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;
import static org.rust.lang.core.lexer.RustTokenElementTypes.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class RustParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    if (t == ABI) {
      r = abi(b, 0);
    }
    else if (t == ANON_PARAM) {
      r = anon_param(b, 0);
    }
    else if (t == ANON_PARAMS) {
      r = anon_params(b, 0);
    }
    else if (t == ARG_LIST) {
      r = arg_list(b, 0);
    }
    else if (t == ARRAY_EXPR) {
      r = array_expr(b, 0);
    }
    else if (t == BINARY_EXPR) {
      r = binary_expr(b, 0);
    }
    else if (t == BINDING) {
      r = binding(b, 0);
    }
    else if (t == BINDING_MODE) {
      r = binding_mode(b, 0);
    }
    else if (t == BINDINGS) {
      r = bindings(b, 0);
    }
    else if (t == BLOCK) {
      r = block(b, 0);
    }
    else if (t == BLOCK_EXPR) {
      r = block_expr(b, 0);
    }
    else if (t == BOUND) {
      r = bound(b, 0);
    }
    else if (t == BOUNDS) {
      r = bounds(b, 0);
    }
    else if (t == BREAK_EXPR) {
      r = break_expr(b, 0);
    }
    else if (t == CALL_EXPR) {
      r = expr(b, 0, 25);
    }
    else if (t == CONST_ITEM) {
      r = const_item(b, 0);
    }
    else if (t == CONT_EXPR) {
      r = cont_expr(b, 0);
    }
    else if (t == DECL_ITEM) {
      r = decl_item(b, 0);
    }
    else if (t == DECL_STMT) {
      r = decl_stmt(b, 0);
    }
    else if (t == ENUM_ARGS) {
      r = enum_args(b, 0);
    }
    else if (t == ENUM_DEF) {
      r = enum_def(b, 0);
    }
    else if (t == ENUM_ITEM) {
      r = enum_item(b, 0);
    }
    else if (t == EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EXPR_STMT) {
      r = expr_stmt(b, 0);
    }
    else if (t == EXTERN_BLOCK) {
      r = extern_block(b, 0);
    }
    else if (t == EXTERN_CRATE_DECL) {
      r = extern_crate_decl(b, 0);
    }
    else if (t == EXTERN_FN_ITEM) {
      r = extern_fn_item(b, 0);
    }
    else if (t == FIELD_EXPR) {
      r = expr(b, 0, 25);
    }
    else if (t == FN_ITEM) {
      r = fn_item(b, 0);
    }
    else if (t == FN_PARAMS) {
      r = fn_params(b, 0);
    }
    else if (t == FOR_EXPR) {
      r = for_expr(b, 0);
    }
    else if (t == FOR_LIFETIMES) {
      r = for_lifetimes(b, 0);
    }
    else if (t == FOREIGN_FN_ITEM) {
      r = foreign_fn_item(b, 0);
    }
    else if (t == FOREIGN_ITEM) {
      r = foreign_item(b, 0);
    }
    else if (t == FOREIGN_MOD_ITEM) {
      r = foreign_mod_item(b, 0);
    }
    else if (t == GENERIC_ARGS) {
      r = generic_args(b, 0);
    }
    else if (t == GENERIC_PARAMS) {
      r = generic_params(b, 0);
    }
    else if (t == IF_EXPR) {
      r = if_expr(b, 0);
    }
    else if (t == IF_LET_EXPR) {
      r = if_let_expr(b, 0);
    }
    else if (t == IMPL_ITEM) {
      r = impl_item(b, 0);
    }
    else if (t == IMPL_METHOD) {
      r = impl_method(b, 0);
    }
    else if (t == INDEX_EXPR) {
      r = expr(b, 0, 25);
    }
    else if (t == INNER_ATTR) {
      r = inner_attr(b, 0);
    }
    else if (t == ITEM) {
      r = item(b, 0);
    }
    else if (t == LAMBDA_EXPR) {
      r = lambda_expr(b, 0);
    }
    else if (t == LET_DECL) {
      r = let_decl(b, 0);
    }
    else if (t == LIFETIMES) {
      r = lifetimes(b, 0);
    }
    else if (t == LIT_EXPR) {
      r = lit_expr(b, 0);
    }
    else if (t == LOOP_EXPR) {
      r = loop_expr(b, 0);
    }
    else if (t == MATCH_EXPR) {
      r = match_expr(b, 0);
    }
    else if (t == META_ITEM) {
      r = meta_item(b, 0);
    }
    else if (t == METHOD) {
      r = method(b, 0);
    }
    else if (t == METHOD_CALL_EXPR) {
      r = expr(b, 0, 25);
    }
    else if (t == MOD_ITEM) {
      r = mod_item(b, 0);
    }
    else if (t == OUTER_ATTR) {
      r = outer_attr(b, 0);
    }
    else if (t == PARAM) {
      r = param(b, 0);
    }
    else if (t == PAREN_EXPR) {
      r = paren_expr(b, 0);
    }
    else if (t == PAT) {
      r = pat(b, 0);
    }
    else if (t == PAT_ENUM) {
      r = pat_enum(b, 0);
    }
    else if (t == PAT_IDENT) {
      r = pat_ident(b, 0);
    }
    else if (t == PAT_QUAL_PATH) {
      r = pat_qual_path(b, 0);
    }
    else if (t == PAT_RANGE) {
      r = pat_range(b, 0);
    }
    else if (t == PAT_REG) {
      r = pat_reg(b, 0);
    }
    else if (t == PAT_STRUCT) {
      r = pat_struct(b, 0);
    }
    else if (t == PAT_STRUCT_FIELDS) {
      r = pat_struct_fields(b, 0);
    }
    else if (t == PAT_TUP) {
      r = pat_tup(b, 0);
    }
    else if (t == PAT_UNIQ) {
      r = pat_uniq(b, 0);
    }
    else if (t == PAT_VEC) {
      r = pat_vec(b, 0);
    }
    else if (t == PAT_WILD) {
      r = pat_wild(b, 0);
    }
    else if (t == PATH) {
      r = path(b, 0);
    }
    else if (t == PATH_EXPR) {
      r = path_expr(b, 0);
    }
    else if (t == PATH_EXPR_PART) {
      r = path_expr_part(b, 0);
    }
    else if (t == PATH_GLOB) {
      r = path_glob(b, 0);
    }
    else if (t == POLYBOUND) {
      r = polybound(b, 0);
    }
    else if (t == RANGE_EXPR) {
      r = range_expr(b, 0);
    }
    else if (t == RET_EXPR) {
      r = ret_expr(b, 0);
    }
    else if (t == RET_TYPE) {
      r = ret_type(b, 0);
    }
    else if (t == SELF_EXPR) {
      r = self_expr(b, 0);
    }
    else if (t == STATIC_ITEM) {
      r = static_item(b, 0);
    }
    else if (t == STMT) {
      r = stmt(b, 0);
    }
    else if (t == STMT_ITEM) {
      r = stmt_item(b, 0);
    }
    else if (t == STRUCT_DECL_ARGS) {
      r = struct_decl_args(b, 0);
    }
    else if (t == STRUCT_DECL_FIELD) {
      r = struct_decl_field(b, 0);
    }
    else if (t == STRUCT_EXPR) {
      r = struct_expr(b, 0);
    }
    else if (t == STRUCT_ITEM) {
      r = struct_item(b, 0);
    }
    else if (t == STRUCT_TUPLE_ARGS) {
      r = struct_tuple_args(b, 0);
    }
    else if (t == STRUCT_TUPLE_FIELD) {
      r = struct_tuple_field(b, 0);
    }
    else if (t == TRAIT_CONST) {
      r = trait_const(b, 0);
    }
    else if (t == TRAIT_ITEM) {
      r = trait_item(b, 0);
    }
    else if (t == TRAIT_METHOD) {
      r = trait_method(b, 0);
    }
    else if (t == TRAIT_REF) {
      r = trait_ref(b, 0);
    }
    else if (t == TRAIT_TYPE) {
      r = trait_type(b, 0);
    }
    else if (t == TUPLE_EXPR) {
      r = tuple_expr(b, 0);
    }
    else if (t == TYPE_ASCRIPTION) {
      r = type_ascription(b, 0);
    }
    else if (t == TYPE_ITEM) {
      r = type_item(b, 0);
    }
    else if (t == TYPE_METHOD) {
      r = type_method(b, 0);
    }
    else if (t == TYPE_PARAM) {
      r = type_param(b, 0);
    }
    else if (t == TYPE_PARAM_BOUNDS) {
      r = type_param_bounds(b, 0);
    }
    else if (t == TYPE_PARAM_DEFAULT) {
      r = type_param_default(b, 0);
    }
    else if (t == TYPE_PRIM_SUM) {
      r = type_prim_sum(b, 0);
    }
    else if (t == TYPE_SUM) {
      r = type_sum(b, 0);
    }
    else if (t == TYPE_SUMS) {
      r = type_sums(b, 0);
    }
    else if (t == UNARY_EXPR) {
      r = unary_expr(b, 0);
    }
    else if (t == UNIT_EXPR) {
      r = unit_expr(b, 0);
    }
    else if (t == USE_ITEM) {
      r = use_item(b, 0);
    }
    else if (t == VIEW_PATH) {
      r = view_path(b, 0);
    }
    else if (t == VIEW_PATH_PART) {
      r = view_path_part(b, 0);
    }
    else if (t == VIEW_PATH_PART_LEFTISH) {
      r = view_path_part_leftish(b, 0);
    }
    else if (t == WHERE_CLAUSE) {
      r = where_clause(b, 0);
    }
    else if (t == WHERE_PRED) {
      r = where_pred(b, 0);
    }
    else if (t == WHILE_EXPR) {
      r = while_expr(b, 0);
    }
    else if (t == WHILE_LET_EXPR) {
      r = while_let_expr(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return crate(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(VIEW_PATH_PART, VIEW_PATH_PART_LEFTISH),
    create_token_set_(DECL_STMT, EXPR_STMT, STMT),
    create_token_set_(PAT, PAT_ENUM, PAT_IDENT, PAT_QUAL_PATH,
      PAT_RANGE, PAT_REG, PAT_STRUCT, PAT_STRUCT_FIELDS,
      PAT_TUP, PAT_UNIQ, PAT_VEC, PAT_WILD),
    create_token_set_(ARRAY_EXPR, BINARY_EXPR, BLOCK_EXPR, BREAK_EXPR,
      CALL_EXPR, CONT_EXPR, EXPR, FIELD_EXPR,
      FOR_EXPR, IF_EXPR, IF_LET_EXPR, INDEX_EXPR,
      LAMBDA_EXPR, LIT_EXPR, LOOP_EXPR, MATCH_EXPR,
      METHOD_CALL_EXPR, PAREN_EXPR, PATH_EXPR, RANGE_EXPR,
      RET_EXPR, SELF_EXPR, STRUCT_EXPR, TUPLE_EXPR,
      UNARY_EXPR, UNIT_EXPR, WHILE_EXPR, WHILE_LET_EXPR),
  };

  /* ********************************************************** */
  // STRING_LITERAL
  public static boolean abi(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "abi")) return false;
    if (!nextTokenIs(b, STRING_LITERAL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STRING_LITERAL);
    exit_section_(b, m, ABI, r);
    return r;
  }

  /* ********************************************************** */
  // [ named_arg COLON ] type
  public static boolean anon_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_param")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, ANON_PARAM, "<anon param>");
    r = anon_param_0(b, l + 1);
    r = r && type(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ named_arg COLON ]
  private static boolean anon_param_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_param_0")) return false;
    anon_param_0_0(b, l + 1);
    return true;
  }

  // named_arg COLON
  private static boolean anon_param_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_param_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = named_arg(b, l + 1);
    r = r && consumeToken(b, COLON);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // anon_param (COMMA anon_param)*
  public static boolean anon_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_params")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ANON_PARAMS, "<anon params>");
    r = anon_param(b, l + 1);
    r = r && anon_params_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (COMMA anon_param)*
  private static boolean anon_params_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_params_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!anon_params_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "anon_params_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA anon_param
  private static boolean anon_params_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_params_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && anon_param(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // [ COMMA (DOTDOTDOT | anon_param anon_params_allow_variadic_tail) ]
  static boolean anon_params_allow_variadic_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_params_allow_variadic_tail")) return false;
    anon_params_allow_variadic_tail_0(b, l + 1);
    return true;
  }

  // COMMA (DOTDOTDOT | anon_param anon_params_allow_variadic_tail)
  private static boolean anon_params_allow_variadic_tail_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_params_allow_variadic_tail_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && anon_params_allow_variadic_tail_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // DOTDOTDOT | anon_param anon_params_allow_variadic_tail
  private static boolean anon_params_allow_variadic_tail_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_params_allow_variadic_tail_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOTDOTDOT);
    if (!r) r = anon_params_allow_variadic_tail_0_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // anon_param anon_params_allow_variadic_tail
  private static boolean anon_params_allow_variadic_tail_0_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "anon_params_allow_variadic_tail_0_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = anon_param(b, l + 1);
    r = r && anon_params_allow_variadic_tail(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LPAREN expr_list RPAREN
  public static boolean arg_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg_list")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ARG_LIST, null);
    r = consumeToken(b, LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, expr_list(b, l + 1));
    r = p && consumeToken(b, RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // expr [ (COMMA expr)+ | SEMICOLON expr ]
  static boolean array_elems(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr(b, l + 1, -1);
    r = r && array_elems_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ (COMMA expr)+ | SEMICOLON expr ]
  private static boolean array_elems_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_1")) return false;
    array_elems_1_0(b, l + 1);
    return true;
  }

  // (COMMA expr)+ | SEMICOLON expr
  private static boolean array_elems_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = array_elems_1_0_0(b, l + 1);
    if (!r) r = array_elems_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA expr)+
  private static boolean array_elems_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = array_elems_1_0_0_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!array_elems_1_0_0_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "array_elems_1_0_0", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA expr
  private static boolean array_elems_1_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_1_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // SEMICOLON expr
  private static boolean array_elems_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMICOLON);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // AS trait_ref
  static boolean as_trait_ref(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "as_trait_ref")) return false;
    if (!nextTokenIs(b, AS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AS);
    r = r && trait_ref(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // outer_attrs? vis
  static boolean attrs_and_vis(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attrs_and_vis")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = attrs_and_vis_0(b, l + 1);
    r = r && vis(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // outer_attrs?
  private static boolean attrs_and_vis_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attrs_and_vis_0")) return false;
    outer_attrs(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // expr +
  public static boolean binary_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "binary_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, BINARY_EXPR, "<binary expr>");
    r = expr(b, l + 1, -1);
    int c = current_position_(b);
    while (r) {
      if (!expr(b, l + 1, -1)) break;
      if (!empty_element_parsed_guard_(b, "binary_expr", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER EQ type
  public static boolean binding(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "binding")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IDENTIFIER, EQ);
    r = r && type(b, l + 1);
    exit_section_(b, m, BINDING, r);
    return r;
  }

  /* ********************************************************** */
  // REF | REF MUT | MUT
  public static boolean binding_mode(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "binding_mode")) return false;
    if (!nextTokenIs(b, "<binding mode>", MUT, REF)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BINDING_MODE, "<binding mode>");
    r = consumeToken(b, REF);
    if (!r) r = parseTokens(b, 0, REF, MUT);
    if (!r) r = consumeToken(b, MUT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // binding (COMMA binding)*
  public static boolean bindings(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bindings")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = binding(b, l + 1);
    r = r && bindings_1(b, l + 1);
    exit_section_(b, m, BINDINGS, r);
    return r;
  }

  // (COMMA binding)*
  private static boolean bindings_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bindings_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!bindings_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "bindings_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA binding
  private static boolean bindings_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bindings_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && binding(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACE stmt* expr? RBRACE
  public static boolean block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, BLOCK, null);
    r = consumeToken(b, LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, block_1(b, l + 1));
    r = p && report_error_(b, block_2(b, l + 1)) && r;
    r = p && consumeToken(b, RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // stmt*
  private static boolean block_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!stmt(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "block_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // expr?
  private static boolean block_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_2")) return false;
    expr(b, l + 1, -1);
    return true;
  }

  /* ********************************************************** */
  // fn_item
  //                            | unsafe_fn_item
  //                            | mod_item
  //                            | foreign_mod_item
  //                            | struct_item
  //                            | enum_item
  //                            | trait_item
  //                            | impl_item
  static boolean block_item_group(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_item_group")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_item(b, l + 1);
    if (!r) r = unsafe_fn_item(b, l + 1);
    if (!r) r = mod_item(b, l + 1);
    if (!r) r = foreign_mod_item(b, l + 1);
    if (!r) r = struct_item(b, l + 1);
    if (!r) r = enum_item(b, l + 1);
    if (!r) r = trait_item(b, l + 1);
    if (!r) r = impl_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // FALSE | TRUE
  static boolean bool_lit(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bool_lit")) return false;
    if (!nextTokenIs(b, "", FALSE, TRUE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FALSE);
    if (!r) r = consumeToken(b, TRUE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // lifetime | trait_ref
  public static boolean bound(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bound")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BOUND, "<bound>");
    r = lifetime(b, l + 1);
    if (!r) r = trait_ref(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // bound (PLUS bound)*
  public static boolean bounds(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bounds")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BOUNDS, "<bounds>");
    r = bound(b, l + 1);
    r = r && bounds_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (PLUS bound)*
  private static boolean bounds_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bounds_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!bounds_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "bounds_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // PLUS bound
  private static boolean bounds_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bounds_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PLUS);
    r = r && bound(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // COMMA [ anon_params COMMA? ]
  static boolean comma_anon_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comma_anon_params")) return false;
    if (!nextTokenIs(b, COMMA)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && comma_anon_params_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ anon_params COMMA? ]
  private static boolean comma_anon_params_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comma_anon_params_1")) return false;
    comma_anon_params_1_0(b, l + 1);
    return true;
  }

  // anon_params COMMA?
  private static boolean comma_anon_params_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comma_anon_params_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = anon_params(b, l + 1);
    r = r && comma_anon_params_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean comma_anon_params_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comma_anon_params_1_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // COMMA [ params COMMA? ]
  static boolean comma_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comma_params")) return false;
    if (!nextTokenIs(b, COMMA)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && comma_params_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ params COMMA? ]
  private static boolean comma_params_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comma_params_1")) return false;
    comma_params_1_0(b, l + 1);
    return true;
  }

  // params COMMA?
  private static boolean comma_params_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comma_params_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = params(b, l + 1);
    r = r && comma_params_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean comma_params_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comma_params_1_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // EQ expr
  static boolean const_default(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "const_default")) return false;
    if (!nextTokenIs(b, EQ)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EQ);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // CONST decl_item EQ expr SEMICOLON
  public static boolean const_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "const_item")) return false;
    if (!nextTokenIs(b, CONST)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CONST);
    r = r && decl_item(b, l + 1);
    r = r && consumeToken(b, EQ);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, CONST_ITEM, r);
    return r;
  }

  /* ********************************************************** */
  // [ SHEBANG_LINE ] [ inner_attrs ] item *
  static boolean crate(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "crate")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = crate_0(b, l + 1);
    r = r && crate_1(b, l + 1);
    r = r && crate_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ SHEBANG_LINE ]
  private static boolean crate_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "crate_0")) return false;
    consumeToken(b, SHEBANG_LINE);
    return true;
  }

  // [ inner_attrs ]
  private static boolean crate_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "crate_1")) return false;
    inner_attrs(b, l + 1);
    return true;
  }

  // item *
  private static boolean crate_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "crate_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "crate_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER | IDENTIFIER AS IDENTIFIER
  static boolean crate_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "crate_name")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = parseTokens(b, 0, IDENTIFIER, AS, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER COLON type
  public static boolean decl_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "decl_item")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IDENTIFIER, COLON);
    r = r && type(b, l + 1);
    exit_section_(b, m, DECL_ITEM, r);
    return r;
  }

  /* ********************************************************** */
  // item | let_decl
  public static boolean decl_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "decl_stmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DECL_STMT, "<decl stmt>");
    r = item(b, l + 1);
    if (!r) r = let_decl(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // ELSE (if_expr | if_let_expr | LBRACE block RBRACE)
  static boolean else_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "else_tail")) return false;
    if (!nextTokenIs(b, ELSE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELSE);
    r = r && else_tail_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // if_expr | if_let_expr | LBRACE block RBRACE
  private static boolean else_tail_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "else_tail_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = if_expr(b, l + 1);
    if (!r) r = if_let_expr(b, l + 1);
    if (!r) r = else_tail_1_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE block RBRACE
  private static boolean else_tail_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "else_tail_1_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACE struct_decl_fields COMMA? RBRACE
  //             | LPAREN type_sums? RPAREN
  //             | EQ expr
  public static boolean enum_args(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_args")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENUM_ARGS, "<enum args>");
    r = enum_args_0(b, l + 1);
    if (!r) r = enum_args_1(b, l + 1);
    if (!r) r = enum_args_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // LBRACE struct_decl_fields COMMA? RBRACE
  private static boolean enum_args_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_args_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && struct_decl_fields(b, l + 1);
    r = r && enum_args_0_2(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean enum_args_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_args_0_2")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  // LPAREN type_sums? RPAREN
  private static boolean enum_args_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_args_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && enum_args_1_1(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_sums?
  private static boolean enum_args_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_args_1_1")) return false;
    type_sums(b, l + 1);
    return true;
  }

  // EQ expr
  private static boolean enum_args_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_args_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EQ);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // attrs_and_vis IDENTIFIER enum_args?
  public static boolean enum_def(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_def")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENUM_DEF, "<enum def>");
    r = attrs_and_vis(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    r = r && enum_def_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // enum_args?
  private static boolean enum_def_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_def_2")) return false;
    enum_args(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // enum_def (COMMA enum_def)*
  static boolean enum_defs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_defs")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = enum_def(b, l + 1);
    r = r && enum_defs_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA enum_def)*
  private static boolean enum_defs_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_defs_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!enum_defs_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "enum_defs_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA enum_def
  private static boolean enum_defs_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_defs_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && enum_def(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ENUM IDENTIFIER generic_params where_clause? LBRACE enum_defs? COMMA? RBRACE
  public static boolean enum_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_item")) return false;
    if (!nextTokenIs(b, ENUM)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ENUM, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && enum_item_3(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && enum_item_5(b, l + 1);
    r = r && enum_item_6(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, ENUM_ITEM, r);
    return r;
  }

  // where_clause?
  private static boolean enum_item_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_item_3")) return false;
    where_clause(b, l + 1);
    return true;
  }

  // enum_defs?
  private static boolean enum_item_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_item_5")) return false;
    enum_defs(b, l + 1);
    return true;
  }

  // COMMA?
  private static boolean enum_item_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_item_6")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // [ expr (COMMA expr)* ]
  static boolean expr_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_list")) return false;
    expr_list_0(b, l + 1);
    return true;
  }

  // expr (COMMA expr)*
  private static boolean expr_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr(b, l + 1, -1);
    r = r && expr_list_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA expr)*
  private static boolean expr_list_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_list_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!expr_list_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "expr_list_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA expr
  private static boolean expr_list_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_list_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // expr SEMICOLON
  public static boolean expr_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_stmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, EXPR_STMT, "<expr stmt>");
    r = expr(b, l + 1, -1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // EXTERN LBRACE foreign_fn_item* RBRACE
  public static boolean extern_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extern_block")) return false;
    if (!nextTokenIs(b, EXTERN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, EXTERN, LBRACE);
    r = r && extern_block_2(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, EXTERN_BLOCK, r);
    return r;
  }

  // foreign_fn_item*
  private static boolean extern_block_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extern_block_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!foreign_fn_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "extern_block_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // EXTERN CRATE crate_name SEMICOLON
  public static boolean extern_crate_decl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extern_crate_decl")) return false;
    if (!nextTokenIs(b, EXTERN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, EXTERN, CRATE);
    r = r && crate_name(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, EXTERN_CRATE_DECL, r);
    return r;
  }

  /* ********************************************************** */
  // EXTERN abi? fn_item
  public static boolean extern_fn_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extern_fn_item")) return false;
    if (!nextTokenIs(b, EXTERN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXTERN);
    r = r && extern_fn_item_1(b, l + 1);
    r = r && fn_item(b, l + 1);
    exit_section_(b, m, EXTERN_FN_ITEM, r);
    return r;
  }

  // abi?
  private static boolean extern_fn_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extern_fn_item_1")) return false;
    abi(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // LPAREN anon_param anon_params_allow_variadic_tail RPAREN
  //                          | LPAREN RPAREN
  static boolean fn_anon_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_anon_params_0(b, l + 1);
    if (!r) r = parseTokens(b, 0, LPAREN, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN anon_param anon_params_allow_variadic_tail RPAREN
  private static boolean fn_anon_params_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && anon_param(b, l + 1);
    r = r && anon_params_allow_variadic_tail(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LPAREN [ AND lifetime? ] MUT? SELF type_ascription? comma_anon_params? RPAREN
  //                                    | LPAREN anon_params RPAREN
  static boolean fn_anon_params_with_self(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_anon_params_with_self_0(b, l + 1);
    if (!r) r = fn_anon_params_with_self_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN [ AND lifetime? ] MUT? SELF type_ascription? comma_anon_params? RPAREN
  private static boolean fn_anon_params_with_self_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && fn_anon_params_with_self_0_1(b, l + 1);
    r = r && fn_anon_params_with_self_0_2(b, l + 1);
    r = r && consumeToken(b, SELF);
    r = r && fn_anon_params_with_self_0_4(b, l + 1);
    r = r && fn_anon_params_with_self_0_5(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ AND lifetime? ]
  private static boolean fn_anon_params_with_self_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self_0_1")) return false;
    fn_anon_params_with_self_0_1_0(b, l + 1);
    return true;
  }

  // AND lifetime?
  private static boolean fn_anon_params_with_self_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AND);
    r = r && fn_anon_params_with_self_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetime?
  private static boolean fn_anon_params_with_self_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self_0_1_0_1")) return false;
    lifetime(b, l + 1);
    return true;
  }

  // MUT?
  private static boolean fn_anon_params_with_self_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self_0_2")) return false;
    consumeToken(b, MUT);
    return true;
  }

  // type_ascription?
  private static boolean fn_anon_params_with_self_0_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self_0_4")) return false;
    type_ascription(b, l + 1);
    return true;
  }

  // comma_anon_params?
  private static boolean fn_anon_params_with_self_0_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self_0_5")) return false;
    comma_anon_params(b, l + 1);
    return true;
  }

  // LPAREN anon_params RPAREN
  private static boolean fn_anon_params_with_self_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_anon_params_with_self_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && anon_params(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // fn_params_allow_variadic ret_type
  static boolean fn_decl_allow_variadic(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_decl_allow_variadic")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_params_allow_variadic(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // fn_params_with_self ret_type
  static boolean fn_decl_with_self(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_decl_with_self")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_params_with_self(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // fn_anon_params_with_self ret_type
  static boolean fn_decl_with_self_allow_anon_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_decl_with_self_allow_anon_params")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_anon_params_with_self(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // FN IDENTIFIER generic_params fn_params ret_type where_clause? block
  public static boolean fn_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_item")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FN_ITEM, "<fn item>");
    r = consumeTokens(b, 2, FN, IDENTIFIER);
    p = r; // pin = 2
    r = r && report_error_(b, generic_params(b, l + 1));
    r = p && report_error_(b, fn_params(b, l + 1)) && r;
    r = p && report_error_(b, ret_type(b, l + 1)) && r;
    r = p && report_error_(b, fn_item_5(b, l + 1)) && r;
    r = p && block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, fn_item_recover_parser_);
    return r || p;
  }

  // where_clause?
  private static boolean fn_item_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_item_5")) return false;
    where_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // !(<< skipUntilEOL >>)
  static boolean fn_item_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_item_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !fn_item_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // << skipUntilEOL >>
  private static boolean fn_item_recover_0(PsiBuilder b, int l) {
    return skipUntilEOL(b, l + 1);
  }

  /* ********************************************************** */
  // LPAREN params RPAREN
  public static boolean fn_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FN_PARAMS, null);
    r = consumeToken(b, LPAREN);
    p = r; // pin = 1
    r = r && report_error_(b, params(b, l + 1));
    r = p && consumeToken(b, RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // LPAREN params COMMA DOTDOTDOT RPAREN
  //                                    | LPAREN params COMMA RPAREN
  //                                    | LPAREN params RPAREN
  //                                    | LPAREN RPAREN
  static boolean fn_params_allow_variadic(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_allow_variadic")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_params_allow_variadic_0(b, l + 1);
    if (!r) r = fn_params_allow_variadic_1(b, l + 1);
    if (!r) r = fn_params_allow_variadic_2(b, l + 1);
    if (!r) r = parseTokens(b, 0, LPAREN, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN params COMMA DOTDOTDOT RPAREN
  private static boolean fn_params_allow_variadic_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_allow_variadic_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && params(b, l + 1);
    r = r && consumeTokens(b, 0, COMMA, DOTDOTDOT, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN params COMMA RPAREN
  private static boolean fn_params_allow_variadic_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_allow_variadic_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && params(b, l + 1);
    r = r && consumeTokens(b, 0, COMMA, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN params RPAREN
  private static boolean fn_params_allow_variadic_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_allow_variadic_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && params(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LPAREN [ [ AND lifetime ] MUT ] SELF type_ascription? comma_params? RPAREN
  //                               | LPAREN params? RPAREN
  static boolean fn_params_with_self(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_params_with_self_0(b, l + 1);
    if (!r) r = fn_params_with_self_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN [ [ AND lifetime ] MUT ] SELF type_ascription? comma_params? RPAREN
  private static boolean fn_params_with_self_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && fn_params_with_self_0_1(b, l + 1);
    r = r && consumeToken(b, SELF);
    r = r && fn_params_with_self_0_3(b, l + 1);
    r = r && fn_params_with_self_0_4(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ [ AND lifetime ] MUT ]
  private static boolean fn_params_with_self_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_0_1")) return false;
    fn_params_with_self_0_1_0(b, l + 1);
    return true;
  }

  // [ AND lifetime ] MUT
  private static boolean fn_params_with_self_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = fn_params_with_self_0_1_0_0(b, l + 1);
    r = r && consumeToken(b, MUT);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ AND lifetime ]
  private static boolean fn_params_with_self_0_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_0_1_0_0")) return false;
    fn_params_with_self_0_1_0_0_0(b, l + 1);
    return true;
  }

  // AND lifetime
  private static boolean fn_params_with_self_0_1_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_0_1_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AND);
    r = r && lifetime(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_ascription?
  private static boolean fn_params_with_self_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_0_3")) return false;
    type_ascription(b, l + 1);
    return true;
  }

  // comma_params?
  private static boolean fn_params_with_self_0_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_0_4")) return false;
    comma_params(b, l + 1);
    return true;
  }

  // LPAREN params? RPAREN
  private static boolean fn_params_with_self_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && fn_params_with_self_1_1(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // params?
  private static boolean fn_params_with_self_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_params_with_self_1_1")) return false;
    params(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // FOR LT lifetimes? GT for_in_type_suffix
  static boolean for_in_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_in_type")) return false;
    if (!nextTokenIs(b, FOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, LT);
    r = r && for_in_type_2(b, l + 1);
    r = r && consumeToken(b, GT);
    r = r && for_in_type_suffix(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetimes?
  private static boolean for_in_type_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_in_type_2")) return false;
    lifetimes(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // type_proc
  //                              | type_bare_fn
  //                              | trait_ref
  //                              | type_closure
  static boolean for_in_type_suffix(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_in_type_suffix")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_proc(b, l + 1);
    if (!r) r = type_bare_fn(b, l + 1);
    if (!r) r = trait_ref(b, l + 1);
    if (!r) r = type_closure(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // FOR LT lifetimes GT
  public static boolean for_lifetimes(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_lifetimes")) return false;
    if (!nextTokenIs(b, FOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, LT);
    r = r && lifetimes(b, l + 1);
    r = r && consumeToken(b, GT);
    exit_section_(b, m, FOR_LIFETIMES, r);
    return r;
  }

  /* ********************************************************** */
  // FOR Q IDENTIFIER
  //                     | FOR IDENTIFIER Q
  static boolean for_sized(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_sized")) return false;
    if (!nextTokenIs(b, FOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parseTokens(b, 0, FOR, Q, IDENTIFIER);
    if (!r) r = parseTokens(b, 0, FOR, IDENTIFIER, Q);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // FN IDENTIFIER generic_params fn_decl_allow_variadic where_clause? SEMICOLON
  public static boolean foreign_fn_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_fn_item")) return false;
    if (!nextTokenIs(b, FN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FN, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && fn_decl_allow_variadic(b, l + 1);
    r = r && foreign_fn_item_4(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, FOREIGN_FN_ITEM, r);
    return r;
  }

  // where_clause?
  private static boolean foreign_fn_item_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_fn_item_4")) return false;
    where_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // attrs_and_vis STATIC  foreign_static_item
  //                 | attrs_and_vis UNSAFE? foreign_fn_item
  public static boolean foreign_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FOREIGN_ITEM, "<foreign item>");
    r = foreign_item_0(b, l + 1);
    if (!r) r = foreign_item_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // attrs_and_vis STATIC  foreign_static_item
  private static boolean foreign_item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_item_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = attrs_and_vis(b, l + 1);
    r = r && consumeToken(b, STATIC);
    r = r && foreign_static_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // attrs_and_vis UNSAFE? foreign_fn_item
  private static boolean foreign_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_item_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = attrs_and_vis(b, l + 1);
    r = r && foreign_item_1_1(b, l + 1);
    r = r && foreign_fn_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // UNSAFE?
  private static boolean foreign_item_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_item_1_1")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  /* ********************************************************** */
  // foreign_item +
  static boolean foreign_items(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_items")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = foreign_item(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!foreign_item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "foreign_items", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // EXTERN abi? LBRACE inner_attrs? foreign_items? RBRACE
  public static boolean foreign_mod_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_mod_item")) return false;
    if (!nextTokenIs(b, EXTERN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXTERN);
    r = r && foreign_mod_item_1(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && foreign_mod_item_3(b, l + 1);
    r = r && foreign_mod_item_4(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, FOREIGN_MOD_ITEM, r);
    return r;
  }

  // abi?
  private static boolean foreign_mod_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_mod_item_1")) return false;
    abi(b, l + 1);
    return true;
  }

  // inner_attrs?
  private static boolean foreign_mod_item_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_mod_item_3")) return false;
    inner_attrs(b, l + 1);
    return true;
  }

  // foreign_items?
  private static boolean foreign_mod_item_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_mod_item_4")) return false;
    foreign_items(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // MUT? IDENTIFIER COLON type SEMICOLON
  static boolean foreign_static_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_static_item")) return false;
    if (!nextTokenIs(b, "", IDENTIFIER, MUT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = foreign_static_item_0(b, l + 1);
    r = r && consumeTokens(b, 0, IDENTIFIER, COLON);
    r = r && type(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, null, r);
    return r;
  }

  // MUT?
  private static boolean foreign_static_item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_static_item_0")) return false;
    consumeToken(b, MUT);
    return true;
  }

  /* ********************************************************** */
  // LT generic_values GT
  public static boolean generic_args(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_args")) return false;
    if (!nextTokenIs(b, LT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && generic_values(b, l + 1);
    r = r && consumeToken(b, GT);
    exit_section_(b, m, GENERIC_ARGS, r);
    return r;
  }

  /* ********************************************************** */
  // [ generic_params_inner ]
  public static boolean generic_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params")) return false;
    Marker m = enter_section_(b, l, _NONE_, GENERIC_PARAMS, "<generic params>");
    generic_params_inner(b, l + 1);
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // LT lifetimes [ COMMA [ type_params COMMA? ] ] GT
  //                                | LT                     type_params COMMA?     GT
  static boolean generic_params_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner")) return false;
    if (!nextTokenIs(b, LT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = generic_params_inner_0(b, l + 1);
    if (!r) r = generic_params_inner_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LT lifetimes [ COMMA [ type_params COMMA? ] ] GT
  private static boolean generic_params_inner_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && lifetimes(b, l + 1);
    r = r && generic_params_inner_0_2(b, l + 1);
    r = r && consumeToken(b, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ COMMA [ type_params COMMA? ] ]
  private static boolean generic_params_inner_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner_0_2")) return false;
    generic_params_inner_0_2_0(b, l + 1);
    return true;
  }

  // COMMA [ type_params COMMA? ]
  private static boolean generic_params_inner_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner_0_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && generic_params_inner_0_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ type_params COMMA? ]
  private static boolean generic_params_inner_0_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner_0_2_0_1")) return false;
    generic_params_inner_0_2_0_1_0(b, l + 1);
    return true;
  }

  // type_params COMMA?
  private static boolean generic_params_inner_0_2_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner_0_2_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_params(b, l + 1);
    r = r && generic_params_inner_0_2_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean generic_params_inner_0_2_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner_0_2_0_1_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  // LT                     type_params COMMA?     GT
  private static boolean generic_params_inner_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && type_params(b, l + 1);
    r = r && generic_params_inner_1_2(b, l + 1);
    r = r && consumeToken(b, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean generic_params_inner_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_params_inner_1_2")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // lifetimes? type_sums_and_or_bindings?
  static boolean generic_values(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_values")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = generic_values_0(b, l + 1);
    r = r && generic_values_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetimes?
  private static boolean generic_values_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_values_0")) return false;
    lifetimes(b, l + 1);
    return true;
  }

  // type_sums_and_or_bindings?
  private static boolean generic_values_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_values_1")) return false;
    type_sums_and_or_bindings(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (IDENTIFIER (COMMA IDENTIFIER)*)?
  static boolean ident_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ident_list")) return false;
    ident_list_0(b, l + 1);
    return true;
  }

  // IDENTIFIER (COMMA IDENTIFIER)*
  private static boolean ident_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ident_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && ident_list_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA IDENTIFIER)*
  private static boolean ident_list_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ident_list_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!ident_list_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "ident_list_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA IDENTIFIER
  private static boolean ident_list_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ident_list_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COMMA, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (IDENTIFIER | SELF) [ AS IDENTIFIER ] (COMMA idents_or_self)*
  static boolean idents_or_self(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idents_or_self")) return false;
    if (!nextTokenIs(b, "", IDENTIFIER, SELF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = idents_or_self_0(b, l + 1);
    r = r && idents_or_self_1(b, l + 1);
    r = r && idents_or_self_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER | SELF
  private static boolean idents_or_self_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idents_or_self_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, SELF);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ AS IDENTIFIER ]
  private static boolean idents_or_self_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idents_or_self_1")) return false;
    parseTokens(b, 0, AS, IDENTIFIER);
    return true;
  }

  // (COMMA idents_or_self)*
  private static boolean idents_or_self_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idents_or_self_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!idents_or_self_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "idents_or_self_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA idents_or_self
  private static boolean idents_or_self_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idents_or_self_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && idents_or_self(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // attrs_and_vis const_item
  static boolean impl_const(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_const")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = attrs_and_vis(b, l + 1);
    r = r && const_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // impl_method
  //                         | impl_const
  //                      /* | attrs_and_vis impl_macro */
  //                         | impl_type
  static boolean impl_contents(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_contents")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = impl_method(b, l + 1);
    if (!r) r = impl_const(b, l + 1);
    if (!r) r = impl_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // UNSAFE? IMPL generic_params type_prim_sum                 where_clause? LBRACE inner_attrs? impl_contents RBRACE
  //             | UNSAFE? IMPL generic_params LPAREN type RPAREN            where_clause? LBRACE inner_attrs? impl_contents RBRACE
  //             | UNSAFE? IMPL generic_params EXCL? trait_ref FOR (type_sum where_clause? LBRACE inner_attrs? impl_contents RBRACE | DOTDOT LBRACE RBRACE)
  public static boolean impl_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item")) return false;
    if (!nextTokenIs(b, "<impl item>", IMPL, UNSAFE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, IMPL_ITEM, "<impl item>");
    r = impl_item_0(b, l + 1);
    if (!r) r = impl_item_1(b, l + 1);
    if (!r) r = impl_item_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // UNSAFE? IMPL generic_params type_prim_sum                 where_clause? LBRACE inner_attrs? impl_contents RBRACE
  private static boolean impl_item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = impl_item_0_0(b, l + 1);
    r = r && consumeToken(b, IMPL);
    r = r && generic_params(b, l + 1);
    r = r && type_prim_sum(b, l + 1);
    r = r && impl_item_0_4(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && impl_item_0_6(b, l + 1);
    r = r && impl_contents(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // UNSAFE?
  private static boolean impl_item_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_0_0")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  // where_clause?
  private static boolean impl_item_0_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_0_4")) return false;
    where_clause(b, l + 1);
    return true;
  }

  // inner_attrs?
  private static boolean impl_item_0_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_0_6")) return false;
    inner_attrs(b, l + 1);
    return true;
  }

  // UNSAFE? IMPL generic_params LPAREN type RPAREN            where_clause? LBRACE inner_attrs? impl_contents RBRACE
  private static boolean impl_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = impl_item_1_0(b, l + 1);
    r = r && consumeToken(b, IMPL);
    r = r && generic_params(b, l + 1);
    r = r && consumeToken(b, LPAREN);
    r = r && type(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    r = r && impl_item_1_6(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && impl_item_1_8(b, l + 1);
    r = r && impl_contents(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // UNSAFE?
  private static boolean impl_item_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_1_0")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  // where_clause?
  private static boolean impl_item_1_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_1_6")) return false;
    where_clause(b, l + 1);
    return true;
  }

  // inner_attrs?
  private static boolean impl_item_1_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_1_8")) return false;
    inner_attrs(b, l + 1);
    return true;
  }

  // UNSAFE? IMPL generic_params EXCL? trait_ref FOR (type_sum where_clause? LBRACE inner_attrs? impl_contents RBRACE | DOTDOT LBRACE RBRACE)
  private static boolean impl_item_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = impl_item_2_0(b, l + 1);
    r = r && consumeToken(b, IMPL);
    r = r && generic_params(b, l + 1);
    r = r && impl_item_2_3(b, l + 1);
    r = r && trait_ref(b, l + 1);
    r = r && consumeToken(b, FOR);
    r = r && impl_item_2_6(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // UNSAFE?
  private static boolean impl_item_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_2_0")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  // EXCL?
  private static boolean impl_item_2_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_2_3")) return false;
    consumeToken(b, EXCL);
    return true;
  }

  // type_sum where_clause? LBRACE inner_attrs? impl_contents RBRACE | DOTDOT LBRACE RBRACE
  private static boolean impl_item_2_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_2_6")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = impl_item_2_6_0(b, l + 1);
    if (!r) r = parseTokens(b, 0, DOTDOT, LBRACE, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_sum where_clause? LBRACE inner_attrs? impl_contents RBRACE
  private static boolean impl_item_2_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_2_6_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_sum(b, l + 1);
    r = r && impl_item_2_6_0_1(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && impl_item_2_6_0_3(b, l + 1);
    r = r && impl_contents(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // where_clause?
  private static boolean impl_item_2_6_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_2_6_0_1")) return false;
    where_clause(b, l + 1);
    return true;
  }

  // inner_attrs?
  private static boolean impl_item_2_6_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item_2_6_0_3")) return false;
    inner_attrs(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // attrs_and_vis UNSAFE? [ EXTERN abi? ] FN IDENTIFIER generic_params fn_decl_with_self where_clause? block
  public static boolean impl_method(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_method")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, IMPL_METHOD, "<impl method>");
    r = attrs_and_vis(b, l + 1);
    r = r && impl_method_1(b, l + 1);
    r = r && impl_method_2(b, l + 1);
    r = r && consumeTokens(b, 0, FN, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && fn_decl_with_self(b, l + 1);
    r = r && impl_method_7(b, l + 1);
    r = r && block(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // UNSAFE?
  private static boolean impl_method_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_method_1")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  // [ EXTERN abi? ]
  private static boolean impl_method_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_method_2")) return false;
    impl_method_2_0(b, l + 1);
    return true;
  }

  // EXTERN abi?
  private static boolean impl_method_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_method_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXTERN);
    r = r && impl_method_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // abi?
  private static boolean impl_method_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_method_2_0_1")) return false;
    abi(b, l + 1);
    return true;
  }

  // where_clause?
  private static boolean impl_method_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_method_7")) return false;
    where_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // attrs_and_vis TYPE IDENTIFIER generic_params EQ type_sum SEMICOLON
  static boolean impl_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_type")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = attrs_and_vis(b, l + 1);
    r = r && consumeTokens(b, 0, TYPE, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && consumeToken(b, EQ);
    r = r && type_sum(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // SHEBANG LBRACK meta_item RBRACK | INNER_DOC_COMMENT
  public static boolean inner_attr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "inner_attr")) return false;
    if (!nextTokenIs(b, "<inner attr>", INNER_DOC_COMMENT, SHEBANG)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, INNER_ATTR, "<inner attr>");
    r = inner_attr_0(b, l + 1);
    if (!r) r = consumeToken(b, INNER_DOC_COMMENT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // SHEBANG LBRACK meta_item RBRACK
  private static boolean inner_attr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "inner_attr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SHEBANG, LBRACK);
    r = r && meta_item(b, l + 1);
    r = r && consumeToken(b, RBRACK);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // inner_attr +
  static boolean inner_attrs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "inner_attrs")) return false;
    if (!nextTokenIs(b, "", INNER_DOC_COMMENT, SHEBANG)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = inner_attr(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!inner_attr(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "inner_attrs", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // outer_attrs? vis?
  //        ( stmt_item
  //        | mod_item
  //     /* | item_macro */)
  public static boolean item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ITEM, "<item>");
    r = item_0(b, l + 1);
    r = r && item_1(b, l + 1);
    r = r && item_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // outer_attrs?
  private static boolean item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item_0")) return false;
    outer_attrs(b, l + 1);
    return true;
  }

  // vis?
  private static boolean item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item_1")) return false;
    vis(b, l + 1);
    return true;
  }

  // stmt_item
  //        | mod_item
  private static boolean item_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = stmt_item(b, l + 1);
    if (!r) r = mod_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LET pat [ COLON type ] [ EQ expr ] SEMICOLON
  public static boolean let_decl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "let_decl")) return false;
    if (!nextTokenIs(b, LET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LET);
    r = r && pat(b, l + 1);
    r = r && let_decl_2(b, l + 1);
    r = r && let_decl_3(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, LET_DECL, r);
    return r;
  }

  // [ COLON type ]
  private static boolean let_decl_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "let_decl_2")) return false;
    let_decl_2_0(b, l + 1);
    return true;
  }

  // COLON type
  private static boolean let_decl_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "let_decl_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ EQ expr ]
  private static boolean let_decl_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "let_decl_3")) return false;
    let_decl_3_0(b, l + 1);
    return true;
  }

  // EQ expr
  private static boolean let_decl_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "let_decl_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EQ);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LIFETIME | STATIC_LIFETIME
  static boolean lifetime(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetime")) return false;
    if (!nextTokenIs(b, "", LIFETIME, STATIC_LIFETIME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LIFETIME);
    if (!r) r = consumeToken(b, STATIC_LIFETIME);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LIFETIME lifetime_bounds? | STATIC_LIFETIME
  static boolean lifetime_and_bounds(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetime_and_bounds")) return false;
    if (!nextTokenIs(b, "", LIFETIME, STATIC_LIFETIME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lifetime_and_bounds_0(b, l + 1);
    if (!r) r = consumeToken(b, STATIC_LIFETIME);
    exit_section_(b, m, null, r);
    return r;
  }

  // LIFETIME lifetime_bounds?
  private static boolean lifetime_and_bounds_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetime_and_bounds_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LIFETIME);
    r = r && lifetime_and_bounds_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetime_bounds?
  private static boolean lifetime_and_bounds_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetime_and_bounds_0_1")) return false;
    lifetime_bounds(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // COLON lifetime (PLUS lifetime)*
  static boolean lifetime_bounds(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetime_bounds")) return false;
    if (!nextTokenIs(b, COLON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && lifetime(b, l + 1);
    r = r && lifetime_bounds_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (PLUS lifetime)*
  private static boolean lifetime_bounds_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetime_bounds_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!lifetime_bounds_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "lifetime_bounds_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // PLUS lifetime
  private static boolean lifetime_bounds_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetime_bounds_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PLUS);
    r = r && lifetime(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // lifetime_and_bounds (COMMA lifetime_and_bounds)*
  public static boolean lifetimes(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetimes")) return false;
    if (!nextTokenIs(b, "<lifetimes>", LIFETIME, STATIC_LIFETIME)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIFETIMES, "<lifetimes>");
    r = lifetime_and_bounds(b, l + 1);
    r = r && lifetimes_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (COMMA lifetime_and_bounds)*
  private static boolean lifetimes_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetimes_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!lifetimes_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "lifetimes_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA lifetime_and_bounds
  private static boolean lifetimes_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lifetimes_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && lifetime_and_bounds(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ( STRING_LITERAL
  //                 | CHAR_LITERAL
  //                 | INTEGER_LITERAL
  //                 | FLOAT_LITERAL
  //                 | bool_lit ) lit_suffix?
  static boolean lit(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lit")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lit_0(b, l + 1);
    r = r && lit_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // STRING_LITERAL
  //                 | CHAR_LITERAL
  //                 | INTEGER_LITERAL
  //                 | FLOAT_LITERAL
  //                 | bool_lit
  private static boolean lit_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lit_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STRING_LITERAL);
    if (!r) r = consumeToken(b, CHAR_LITERAL);
    if (!r) r = consumeToken(b, INTEGER_LITERAL);
    if (!r) r = consumeToken(b, FLOAT_LITERAL);
    if (!r) r = bool_lit(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lit_suffix?
  private static boolean lit_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lit_1")) return false;
    lit_suffix(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // path_expr | lit | MINUS lit
  static boolean lit_or_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lit_or_path")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_expr(b, l + 1);
    if (!r) r = lit(b, l + 1);
    if (!r) r = lit_or_path_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // MINUS lit
  private static boolean lit_or_path_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lit_or_path_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, MINUS);
    r = r && lit(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  static boolean lit_suffix(PsiBuilder b, int l) {
    return consumeToken(b, IDENTIFIER);
  }

  /* ********************************************************** */
  // outer_attrs* match_pat ARROW (expr COMMA | LBRACE block RBRACE)
  static boolean match_arm(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_arm")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = match_arm_0(b, l + 1);
    r = r && match_pat(b, l + 1);
    r = r && consumeToken(b, ARROW);
    r = r && match_arm_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // outer_attrs*
  private static boolean match_arm_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_arm_0")) return false;
    int c = current_position_(b);
    while (true) {
      if (!outer_attrs(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "match_arm_0", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // expr COMMA | LBRACE block RBRACE
  private static boolean match_arm_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_arm_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = match_arm_3_0(b, l + 1);
    if (!r) r = match_arm_3_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // expr COMMA
  private static boolean match_arm_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_arm_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr(b, l + 1, -1);
    r = r && consumeToken(b, COMMA);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE block RBRACE
  private static boolean match_arm_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_arm_3_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // pat (OR pat)* (IF expr)?
  static boolean match_pat(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_pat")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat(b, l + 1);
    r = r && match_pat_1(b, l + 1);
    r = r && match_pat_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (OR pat)*
  private static boolean match_pat_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_pat_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!match_pat_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "match_pat_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // OR pat
  private static boolean match_pat_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_pat_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OR);
    r = r && pat(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (IF expr)?
  private static boolean match_pat_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_pat_2")) return false;
    match_pat_2_0(b, l + 1);
    return true;
  }

  // IF expr
  private static boolean match_pat_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_pat_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IF);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER [ EQ lit | LPAREN meta_seq RPAREN ]
  public static boolean meta_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && meta_item_1(b, l + 1);
    exit_section_(b, m, META_ITEM, r);
    return r;
  }

  // [ EQ lit | LPAREN meta_seq RPAREN ]
  private static boolean meta_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_1")) return false;
    meta_item_1_0(b, l + 1);
    return true;
  }

  // EQ lit | LPAREN meta_seq RPAREN
  private static boolean meta_item_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = meta_item_1_0_0(b, l + 1);
    if (!r) r = meta_item_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // EQ lit
  private static boolean meta_item_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EQ);
    r = r && lit(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN meta_seq RPAREN
  private static boolean meta_item_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && meta_seq(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // meta_item [ COMMA meta_seq? ]
  static boolean meta_seq(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_seq")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = meta_item(b, l + 1);
    r = r && meta_seq_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ COMMA meta_seq? ]
  private static boolean meta_seq_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_seq_1")) return false;
    meta_seq_1_0(b, l + 1);
    return true;
  }

  // COMMA meta_seq?
  private static boolean meta_seq_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_seq_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && meta_seq_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // meta_seq?
  private static boolean meta_seq_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_seq_1_0_1")) return false;
    meta_seq(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // attrs_and_vis UNSAFE? [ EXTERN abi? ] FN IDENTIFIER generic_params fn_decl_with_self_allow_anon_params where_clause? block
  public static boolean method(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, METHOD, "<method>");
    r = attrs_and_vis(b, l + 1);
    r = r && method_1(b, l + 1);
    r = r && method_2(b, l + 1);
    r = r && consumeTokens(b, 0, FN, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && fn_decl_with_self_allow_anon_params(b, l + 1);
    r = r && method_7(b, l + 1);
    r = r && block(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // UNSAFE?
  private static boolean method_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_1")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  // [ EXTERN abi? ]
  private static boolean method_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_2")) return false;
    method_2_0(b, l + 1);
    return true;
  }

  // EXTERN abi?
  private static boolean method_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXTERN);
    r = r && method_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // abi?
  private static boolean method_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_2_0_1")) return false;
    abi(b, l + 1);
    return true;
  }

  // where_clause?
  private static boolean method_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_7")) return false;
    where_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (attrs_and_vis item)+
  static boolean mod_contents(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_contents")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = mod_contents_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!mod_contents_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "mod_contents", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // attrs_and_vis item
  private static boolean mod_contents_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_contents_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = attrs_and_vis(b, l + 1);
    r = r && item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // MOD IDENTIFIER (SEMICOLON | LBRACE inner_attrs? mod_contents? RBRACE)
  public static boolean mod_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_item")) return false;
    if (!nextTokenIs(b, MOD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, MOD, IDENTIFIER);
    r = r && mod_item_2(b, l + 1);
    exit_section_(b, m, MOD_ITEM, r);
    return r;
  }

  // SEMICOLON | LBRACE inner_attrs? mod_contents? RBRACE
  private static boolean mod_item_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_item_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMICOLON);
    if (!r) r = mod_item_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE inner_attrs? mod_contents? RBRACE
  private static boolean mod_item_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_item_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && mod_item_2_1_1(b, l + 1);
    r = r && mod_item_2_1_2(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // inner_attrs?
  private static boolean mod_item_2_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_item_2_1_1")) return false;
    inner_attrs(b, l + 1);
    return true;
  }

  // mod_contents?
  private static boolean mod_item_2_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_item_2_1_2")) return false;
    mod_contents(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER
  //                     | UNDERSCORE
  //                     | AND (IDENTIFIER | UNDERSCORE)
  //                     | ANDAND (IDENTIFIER | UNDERSCORE)
  //                     | MUT IDENTIFIER
  static boolean named_arg(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "named_arg")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, UNDERSCORE);
    if (!r) r = named_arg_2(b, l + 1);
    if (!r) r = named_arg_3(b, l + 1);
    if (!r) r = parseTokens(b, 0, MUT, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  // AND (IDENTIFIER | UNDERSCORE)
  private static boolean named_arg_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "named_arg_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AND);
    r = r && named_arg_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER | UNDERSCORE
  private static boolean named_arg_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "named_arg_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, UNDERSCORE);
    exit_section_(b, m, null, r);
    return r;
  }

  // ANDAND (IDENTIFIER | UNDERSCORE)
  private static boolean named_arg_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "named_arg_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ANDAND);
    r = r && named_arg_3_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER | UNDERSCORE
  private static boolean named_arg_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "named_arg_3_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, UNDERSCORE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'abc7'
  static boolean no_struct_lit_expr(PsiBuilder b, int l) {
    return consumeToken(b, "abc7");
  }

  /* ********************************************************** */
  // SHA LBRACK meta_item RBRACK | OUTER_DOC_COMMENT
  public static boolean outer_attr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "outer_attr")) return false;
    if (!nextTokenIs(b, "<outer attr>", OUTER_DOC_COMMENT, SHA)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OUTER_ATTR, "<outer attr>");
    r = outer_attr_0(b, l + 1);
    if (!r) r = consumeToken(b, OUTER_DOC_COMMENT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // SHA LBRACK meta_item RBRACK
  private static boolean outer_attr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "outer_attr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SHA, LBRACK);
    r = r && meta_item(b, l + 1);
    r = r && consumeToken(b, RBRACK);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // outer_attr +
  static boolean outer_attrs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "outer_attrs")) return false;
    if (!nextTokenIs(b, "", OUTER_DOC_COMMENT, SHA)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = outer_attr(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!outer_attr(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "outer_attrs", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // pat COLON type_sum
  public static boolean param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "param")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PARAM, "<param>");
    r = pat(b, l + 1);
    r = r && consumeToken(b, COLON);
    r = r && type_sum(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // [ param (COMMA param)* ]
  static boolean params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "params")) return false;
    params_0(b, l + 1);
    return true;
  }

  // param (COMMA param)*
  private static boolean params_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "params_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = param(b, l + 1);
    r = r && params_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA param)*
  private static boolean params_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "params_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!params_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "params_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA param
  private static boolean params_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "params_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && param(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // pat_wild
  //       | pat_reg
  //       | pat_tup
  //       | pat_vec
  //       | pat_ident
  //       | pat_range
  //       | pat_struct
  //       | pat_enum
  //    /* | path_expr EXCL maybe_ident delimited_token_trees */
  //       | pat_uniq
  //       | pat_qual_path
  public static boolean pat(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, PAT, "<pat>");
    r = pat_wild(b, l + 1);
    if (!r) r = pat_reg(b, l + 1);
    if (!r) r = pat_tup(b, l + 1);
    if (!r) r = pat_vec(b, l + 1);
    if (!r) r = pat_ident(b, l + 1);
    if (!r) r = pat_range(b, l + 1);
    if (!r) r = pat_struct(b, l + 1);
    if (!r) r = pat_enum(b, l + 1);
    if (!r) r = pat_uniq(b, l + 1);
    if (!r) r = pat_qual_path(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // path_expr LPAREN (DOTDOT | pat_tup_elts) RPAREN
  public static boolean pat_enum(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_enum")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PAT_ENUM, "<pat enum>");
    r = path_expr(b, l + 1);
    r = r && consumeToken(b, LPAREN);
    r = r && pat_enum_2(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // DOTDOT | pat_tup_elts
  private static boolean pat_enum_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_enum_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOTDOT);
    if (!r) r = pat_tup_elts(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // binding_mode?  IDENTIFIER [ COLON pat ]
  //                       | BOX binding_mode?  IDENTIFIER
  static boolean pat_field(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_field")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat_field_0(b, l + 1);
    if (!r) r = pat_field_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // binding_mode?  IDENTIFIER [ COLON pat ]
  private static boolean pat_field_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_field_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat_field_0_0(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    r = r && pat_field_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // binding_mode?
  private static boolean pat_field_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_field_0_0")) return false;
    binding_mode(b, l + 1);
    return true;
  }

  // [ COLON pat ]
  private static boolean pat_field_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_field_0_2")) return false;
    pat_field_0_2_0(b, l + 1);
    return true;
  }

  // COLON pat
  private static boolean pat_field_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_field_0_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && pat(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // BOX binding_mode?  IDENTIFIER
  private static boolean pat_field_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_field_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BOX);
    r = r && pat_field_1_1(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  // binding_mode?
  private static boolean pat_field_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_field_1_1")) return false;
    binding_mode(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // pat_field (COMMA pat_field)
  static boolean pat_fields(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_fields")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat_field(b, l + 1);
    r = r && pat_fields_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA pat_field
  private static boolean pat_fields_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_fields_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && pat_field(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // binding_mode IDENTIFIER
  //                   |              IDENTIFIER AT pat
  //                   | binding_mode IDENTIFIER AT pat
  public static boolean pat_ident(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_ident")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PAT_IDENT, "<pat ident>");
    r = pat_ident_0(b, l + 1);
    if (!r) r = pat_ident_1(b, l + 1);
    if (!r) r = pat_ident_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // binding_mode IDENTIFIER
  private static boolean pat_ident_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_ident_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = binding_mode(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER AT pat
  private static boolean pat_ident_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_ident_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IDENTIFIER, AT);
    r = r && pat(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // binding_mode IDENTIFIER AT pat
  private static boolean pat_ident_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_ident_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = binding_mode(b, l + 1);
    r = r && consumeTokens(b, 0, IDENTIFIER, AT);
    r = r && pat(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LT type_sum as_trait_ref? GT COLONCOLON IDENTIFIER
  public static boolean pat_qual_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_qual_path")) return false;
    if (!nextTokenIs(b, LT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && type_sum(b, l + 1);
    r = r && pat_qual_path_2(b, l + 1);
    r = r && consumeTokens(b, 0, GT, COLONCOLON, IDENTIFIER);
    exit_section_(b, m, PAT_QUAL_PATH, r);
    return r;
  }

  // as_trait_ref?
  private static boolean pat_qual_path_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_qual_path_2")) return false;
    as_trait_ref(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // lit_or_path [ DOTDOTDOT lit_or_path ]
  public static boolean pat_range(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_range")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PAT_RANGE, "<pat range>");
    r = lit_or_path(b, l + 1);
    r = r && pat_range_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ DOTDOTDOT lit_or_path ]
  private static boolean pat_range_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_range_1")) return false;
    pat_range_1_0(b, l + 1);
    return true;
  }

  // DOTDOTDOT lit_or_path
  private static boolean pat_range_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_range_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOTDOTDOT);
    r = r && lit_or_path(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // AND MUT? pat
  //           | ANDAND pat
  public static boolean pat_reg(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_reg")) return false;
    if (!nextTokenIs(b, "<pat reg>", AND, ANDAND)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PAT_REG, "<pat reg>");
    r = pat_reg_0(b, l + 1);
    if (!r) r = pat_reg_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // AND MUT? pat
  private static boolean pat_reg_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_reg_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AND);
    r = r && pat_reg_0_1(b, l + 1);
    r = r && pat(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // MUT?
  private static boolean pat_reg_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_reg_0_1")) return false;
    consumeToken(b, MUT);
    return true;
  }

  // ANDAND pat
  private static boolean pat_reg_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_reg_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ANDAND);
    r = r && pat(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // path_expr LBRACE pat_struct_fields RBRACE
  public static boolean pat_struct(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_struct")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PAT_STRUCT, "<pat struct>");
    r = path_expr(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && pat_struct_fields(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // pat_fields [ COMMA DOTDOT ]
  //                     |                    DOTDOT
  public static boolean pat_struct_fields(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_struct_fields")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PAT_STRUCT_FIELDS, "<pat struct fields>");
    r = pat_struct_fields_0(b, l + 1);
    if (!r) r = consumeToken(b, DOTDOT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // pat_fields [ COMMA DOTDOT ]
  private static boolean pat_struct_fields_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_struct_fields_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat_fields(b, l + 1);
    r = r && pat_struct_fields_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ COMMA DOTDOT ]
  private static boolean pat_struct_fields_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_struct_fields_0_1")) return false;
    parseTokens(b, 0, COMMA, DOTDOT);
    return true;
  }

  /* ********************************************************** */
  // LPAREN [ pat_tup_elts COMMA? ] RPAREN
  public static boolean pat_tup(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_tup")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && pat_tup_1(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, PAT_TUP, r);
    return r;
  }

  // [ pat_tup_elts COMMA? ]
  private static boolean pat_tup_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_tup_1")) return false;
    pat_tup_1_0(b, l + 1);
    return true;
  }

  // pat_tup_elts COMMA?
  private static boolean pat_tup_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_tup_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat_tup_elts(b, l + 1);
    r = r && pat_tup_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean pat_tup_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_tup_1_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // pat (COMMA pat)*
  static boolean pat_tup_elts(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_tup_elts")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat(b, l + 1);
    r = r && pat_tup_elts_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA pat)*
  private static boolean pat_tup_elts_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_tup_elts_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!pat_tup_elts_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pat_tup_elts_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA pat
  private static boolean pat_tup_elts_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_tup_elts_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && pat(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // BOX pat
  public static boolean pat_uniq(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_uniq")) return false;
    if (!nextTokenIs(b, BOX)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BOX);
    r = r && pat(b, l + 1);
    exit_section_(b, m, PAT_UNIQ, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACK pat_vec_elts RBRACK
  public static boolean pat_vec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec")) return false;
    if (!nextTokenIs(b, LBRACK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACK);
    r = r && pat_vec_elts(b, l + 1);
    r = r && consumeToken(b, RBRACK);
    exit_section_(b, m, PAT_VEC, r);
    return r;
  }

  /* ********************************************************** */
  // [ pat_tup_elts COMMA? ] [ DOTDOT [ COMMA pat_tup_elts COMMA? ] ]
  static boolean pat_vec_elts(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat_vec_elts_0(b, l + 1);
    r = r && pat_vec_elts_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ pat_tup_elts COMMA? ]
  private static boolean pat_vec_elts_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts_0")) return false;
    pat_vec_elts_0_0(b, l + 1);
    return true;
  }

  // pat_tup_elts COMMA?
  private static boolean pat_vec_elts_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat_tup_elts(b, l + 1);
    r = r && pat_vec_elts_0_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean pat_vec_elts_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts_0_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  // [ DOTDOT [ COMMA pat_tup_elts COMMA? ] ]
  private static boolean pat_vec_elts_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts_1")) return false;
    pat_vec_elts_1_0(b, l + 1);
    return true;
  }

  // DOTDOT [ COMMA pat_tup_elts COMMA? ]
  private static boolean pat_vec_elts_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOTDOT);
    r = r && pat_vec_elts_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ COMMA pat_tup_elts COMMA? ]
  private static boolean pat_vec_elts_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts_1_0_1")) return false;
    pat_vec_elts_1_0_1_0(b, l + 1);
    return true;
  }

  // COMMA pat_tup_elts COMMA?
  private static boolean pat_vec_elts_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && pat_tup_elts(b, l + 1);
    r = r && pat_vec_elts_1_0_1_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean pat_vec_elts_1_0_1_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_vec_elts_1_0_1_0_2")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // UNDERSCORE
  public static boolean pat_wild(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pat_wild")) return false;
    if (!nextTokenIs(b, UNDERSCORE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, UNDERSCORE);
    exit_section_(b, m, PAT_WILD, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER | SELF
  public static boolean path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path")) return false;
    if (!nextTokenIs(b, "<path>", IDENTIFIER, SELF)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATH, "<path>");
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, SELF);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // COLONCOLON (IDENTIFIER | generic_args)
  public static boolean path_expr_part(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_expr_part")) return false;
    if (!nextTokenIs(b, COLONCOLON)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, PATH_EXPR_PART, null);
    r = consumeToken(b, COLONCOLON);
    r = r && path_expr_part_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // IDENTIFIER | generic_args
  private static boolean path_expr_part_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_expr_part_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = generic_args(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean path_expr_part_leftish(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_expr_part_leftish")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, PATH_EXPR_PART, r);
    return r;
  }

  /* ********************************************************** */
  // path_expr_part_leftish path_expr_part*
  static boolean path_generic_args_with_colons(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_with_colons")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_expr_part_leftish(b, l + 1);
    r = r && path_generic_args_with_colons_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // path_expr_part*
  private static boolean path_generic_args_with_colons_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_with_colons_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!path_expr_part(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "path_generic_args_with_colons_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER                                     path_generic_args_without_colons_right
  //                                            | IDENTIFIER generic_args                        path_generic_args_without_colons_right
  //                                            | IDENTIFIER LPAREN type_sums? RPAREN ret_type   path_generic_args_without_colons_right
  static boolean path_generic_args_without_colons(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_generic_args_without_colons_0(b, l + 1);
    if (!r) r = path_generic_args_without_colons_1(b, l + 1);
    if (!r) r = path_generic_args_without_colons_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER                                     path_generic_args_without_colons_right
  private static boolean path_generic_args_without_colons_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && path_generic_args_without_colons_right(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER generic_args                        path_generic_args_without_colons_right
  private static boolean path_generic_args_without_colons_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && generic_args(b, l + 1);
    r = r && path_generic_args_without_colons_right(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER LPAREN type_sums? RPAREN ret_type   path_generic_args_without_colons_right
  private static boolean path_generic_args_without_colons_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IDENTIFIER, LPAREN);
    r = r && path_generic_args_without_colons_2_2(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    r = r && ret_type(b, l + 1);
    r = r && path_generic_args_without_colons_right(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_sums?
  private static boolean path_generic_args_without_colons_2_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_2_2")) return false;
    type_sums(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // [ COLONCOLON IDENTIFIER                                    path_generic_args_without_colons_right
  //                                                    | COLONCOLON IDENTIFIER generic_args                       path_generic_args_without_colons_right
  //                                                    | COLONCOLON IDENTIFIER LPAREN type_sums? RPAREN ret_type  path_generic_args_without_colons_right ]
  static boolean path_generic_args_without_colons_right(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_right")) return false;
    path_generic_args_without_colons_right_0(b, l + 1);
    return true;
  }

  // COLONCOLON IDENTIFIER                                    path_generic_args_without_colons_right
  //                                                    | COLONCOLON IDENTIFIER generic_args                       path_generic_args_without_colons_right
  //                                                    | COLONCOLON IDENTIFIER LPAREN type_sums? RPAREN ret_type  path_generic_args_without_colons_right
  private static boolean path_generic_args_without_colons_right_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_right_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_generic_args_without_colons_right_0_0(b, l + 1);
    if (!r) r = path_generic_args_without_colons_right_0_1(b, l + 1);
    if (!r) r = path_generic_args_without_colons_right_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COLONCOLON IDENTIFIER                                    path_generic_args_without_colons_right
  private static boolean path_generic_args_without_colons_right_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_right_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COLONCOLON, IDENTIFIER);
    r = r && path_generic_args_without_colons_right(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COLONCOLON IDENTIFIER generic_args                       path_generic_args_without_colons_right
  private static boolean path_generic_args_without_colons_right_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_right_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COLONCOLON, IDENTIFIER);
    r = r && generic_args(b, l + 1);
    r = r && path_generic_args_without_colons_right(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COLONCOLON IDENTIFIER LPAREN type_sums? RPAREN ret_type  path_generic_args_without_colons_right
  private static boolean path_generic_args_without_colons_right_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_right_0_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COLONCOLON, IDENTIFIER, LPAREN);
    r = r && path_generic_args_without_colons_right_0_2_3(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    r = r && ret_type(b, l + 1);
    r = r && path_generic_args_without_colons_right(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_sums?
  private static boolean path_generic_args_without_colons_right_0_2_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_generic_args_without_colons_right_0_2_3")) return false;
    type_sums(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER (COLONCOLON (path_glob | MUL))?
  //             | LBRACE path (COMMA path)* RBRACE
  public static boolean path_glob(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob")) return false;
    if (!nextTokenIs(b, "<path glob>", IDENTIFIER, LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATH_GLOB, "<path glob>");
    r = path_glob_0(b, l + 1);
    if (!r) r = path_glob_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // IDENTIFIER (COLONCOLON (path_glob | MUL))?
  private static boolean path_glob_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && path_glob_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COLONCOLON (path_glob | MUL))?
  private static boolean path_glob_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob_0_1")) return false;
    path_glob_0_1_0(b, l + 1);
    return true;
  }

  // COLONCOLON (path_glob | MUL)
  private static boolean path_glob_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLONCOLON);
    r = r && path_glob_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // path_glob | MUL
  private static boolean path_glob_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob_0_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_glob(b, l + 1);
    if (!r) r = consumeToken(b, MUL);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE path (COMMA path)* RBRACE
  private static boolean path_glob_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && path(b, l + 1);
    r = r && path_glob_1_2(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA path)*
  private static boolean path_glob_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob_1_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!path_glob_1_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "path_glob_1_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA path
  private static boolean path_glob_1_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob_1_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && path(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // view_path_part_leftish view_path_part*
  static boolean path_no_types_allowed(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_no_types_allowed")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = view_path_part_leftish(b, l + 1);
    r = r && path_no_types_allowed_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // view_path_part*
  private static boolean path_no_types_allowed_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_no_types_allowed_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!view_path_part(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "path_no_types_allowed_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // [ FOR LT lifetimes? GT | Q ] bound
  public static boolean polybound(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "polybound")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, POLYBOUND, "<polybound>");
    r = polybound_0(b, l + 1);
    r = r && bound(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ FOR LT lifetimes? GT | Q ]
  private static boolean polybound_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "polybound_0")) return false;
    polybound_0_0(b, l + 1);
    return true;
  }

  // FOR LT lifetimes? GT | Q
  private static boolean polybound_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "polybound_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = polybound_0_0_0(b, l + 1);
    if (!r) r = consumeToken(b, Q);
    exit_section_(b, m, null, r);
    return r;
  }

  // FOR LT lifetimes? GT
  private static boolean polybound_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "polybound_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, LT);
    r = r && polybound_0_0_0_2(b, l + 1);
    r = r && consumeToken(b, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetimes?
  private static boolean polybound_0_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "polybound_0_0_0_2")) return false;
    lifetimes(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // expr +
  public static boolean range_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "range_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, RANGE_EXPR, "<range expr>");
    r = expr(b, l + 1, -1);
    int c = current_position_(b);
    while (r) {
      if (!expr(b, l + 1, -1)) break;
      if (!empty_element_parsed_guard_(b, "range_expr", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // ARROW (type | EXCL)
  public static boolean ret_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ret_type")) return false;
    if (!nextTokenIs(b, ARROW)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, RET_TYPE, null);
    r = consumeToken(b, ARROW);
    p = r; // pin = 1
    r = r && ret_type_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // type | EXCL
  private static boolean ret_type_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ret_type_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type(b, l + 1);
    if (!r) r = consumeToken(b, EXCL);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // STATIC MUT? decl_item EQ expr SEMICOLON
  public static boolean static_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "static_item")) return false;
    if (!nextTokenIs(b, STATIC)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STATIC);
    r = r && static_item_1(b, l + 1);
    r = r && decl_item(b, l + 1);
    r = r && consumeToken(b, EQ);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, STATIC_ITEM, r);
    return r;
  }

  // MUT?
  private static boolean static_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "static_item_1")) return false;
    consumeToken(b, MUT);
    return true;
  }

  /* ********************************************************** */
  // expr_stmt | decl_stmt
  public static boolean stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, STMT, "<stmt>");
    r = expr_stmt(b, l + 1);
    if (!r) r = decl_stmt(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // static_item
  //             | const_item
  //             | type_item
  //             | block_item_group
  //             | view_item
  public static boolean stmt_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt_item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STMT_ITEM, "<stmt item>");
    r = static_item(b, l + 1);
    if (!r) r = const_item(b, l + 1);
    if (!r) r = type_item(b, l + 1);
    if (!r) r = block_item_group(b, l + 1);
    if (!r) r = view_item(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // LBRACE struct_decl_fields COMMA? RBRACE
  public static boolean struct_decl_args(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_decl_args")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && struct_decl_fields(b, l + 1);
    r = r && struct_decl_args_2(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, STRUCT_DECL_ARGS, r);
    return r;
  }

  // COMMA?
  private static boolean struct_decl_args_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_decl_args_2")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // attrs_and_vis IDENTIFIER COLON type_sum
  public static boolean struct_decl_field(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_decl_field")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRUCT_DECL_FIELD, "<struct decl field>");
    r = attrs_and_vis(b, l + 1);
    r = r && consumeTokens(b, 0, IDENTIFIER, COLON);
    r = r && type_sum(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // [ struct_decl_field (COMMA struct_decl_field)* ]
  static boolean struct_decl_fields(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_decl_fields")) return false;
    struct_decl_fields_0(b, l + 1);
    return true;
  }

  // struct_decl_field (COMMA struct_decl_field)*
  private static boolean struct_decl_fields_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_decl_fields_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = struct_decl_field(b, l + 1);
    r = r && struct_decl_fields_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA struct_decl_field)*
  private static boolean struct_decl_fields_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_decl_fields_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!struct_decl_fields_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "struct_decl_fields_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA struct_decl_field
  private static boolean struct_decl_fields_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_decl_fields_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && struct_decl_field(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // STRUCT IDENTIFIER generic_params (struct_tuple_args? where_clause? SEMICOLON | where_clause? struct_decl_args)
  public static boolean struct_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_item")) return false;
    if (!nextTokenIs(b, STRUCT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, STRUCT, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && struct_item_3(b, l + 1);
    exit_section_(b, m, STRUCT_ITEM, r);
    return r;
  }

  // struct_tuple_args? where_clause? SEMICOLON | where_clause? struct_decl_args
  private static boolean struct_item_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_item_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = struct_item_3_0(b, l + 1);
    if (!r) r = struct_item_3_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // struct_tuple_args? where_clause? SEMICOLON
  private static boolean struct_item_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_item_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = struct_item_3_0_0(b, l + 1);
    r = r && struct_item_3_0_1(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, null, r);
    return r;
  }

  // struct_tuple_args?
  private static boolean struct_item_3_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_item_3_0_0")) return false;
    struct_tuple_args(b, l + 1);
    return true;
  }

  // where_clause?
  private static boolean struct_item_3_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_item_3_0_1")) return false;
    where_clause(b, l + 1);
    return true;
  }

  // where_clause? struct_decl_args
  private static boolean struct_item_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_item_3_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = struct_item_3_1_0(b, l + 1);
    r = r && struct_decl_args(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // where_clause?
  private static boolean struct_item_3_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_item_3_1_0")) return false;
    where_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // LPAREN struct_tuple_fields COMMA? RPAREN
  public static boolean struct_tuple_args(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_tuple_args")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && struct_tuple_fields(b, l + 1);
    r = r && struct_tuple_args_2(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, STRUCT_TUPLE_ARGS, r);
    return r;
  }

  // COMMA?
  private static boolean struct_tuple_args_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_tuple_args_2")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // attrs_and_vis type_sum
  public static boolean struct_tuple_field(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_tuple_field")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRUCT_TUPLE_FIELD, "<struct tuple field>");
    r = attrs_and_vis(b, l + 1);
    r = r && type_sum(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // struct_tuple_field (COMMA struct_tuple_field)*
  static boolean struct_tuple_fields(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_tuple_fields")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = struct_tuple_field(b, l + 1);
    r = r && struct_tuple_fields_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA struct_tuple_field)*
  private static boolean struct_tuple_fields_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_tuple_fields_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!struct_tuple_fields_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "struct_tuple_fields_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA struct_tuple_field
  private static boolean struct_tuple_fields_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_tuple_fields_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && struct_tuple_field(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // outer_attrs? CONST IDENTIFIER type_ascription? const_default? SEMICOLON
  public static boolean trait_const(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_const")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TRAIT_CONST, "<trait const>");
    r = trait_const_0(b, l + 1);
    r = r && consumeTokens(b, 0, CONST, IDENTIFIER);
    r = r && trait_const_3(b, l + 1);
    r = r && trait_const_4(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // outer_attrs?
  private static boolean trait_const_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_const_0")) return false;
    outer_attrs(b, l + 1);
    return true;
  }

  // type_ascription?
  private static boolean trait_const_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_const_3")) return false;
    type_ascription(b, l + 1);
    return true;
  }

  // const_default?
  private static boolean trait_const_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_const_4")) return false;
    const_default(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // trait_const | trait_type | trait_method
  static boolean trait_contents(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_contents")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = trait_const(b, l + 1);
    if (!r) r = trait_type(b, l + 1);
    if (!r) r = trait_method(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // UNSAFE? TRAIT IDENTIFIER generic_params for_sized? type_param_bounds? where_clause? LBRACE trait_contents? RBRACE
  public static boolean trait_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_item")) return false;
    if (!nextTokenIs(b, "<trait item>", TRAIT, UNSAFE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TRAIT_ITEM, "<trait item>");
    r = trait_item_0(b, l + 1);
    r = r && consumeTokens(b, 0, TRAIT, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && trait_item_4(b, l + 1);
    r = r && trait_item_5(b, l + 1);
    r = r && trait_item_6(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && trait_item_8(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // UNSAFE?
  private static boolean trait_item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_item_0")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  // for_sized?
  private static boolean trait_item_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_item_4")) return false;
    for_sized(b, l + 1);
    return true;
  }

  // type_param_bounds?
  private static boolean trait_item_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_item_5")) return false;
    type_param_bounds(b, l + 1);
    return true;
  }

  // where_clause?
  private static boolean trait_item_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_item_6")) return false;
    where_clause(b, l + 1);
    return true;
  }

  // trait_contents?
  private static boolean trait_item_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_item_8")) return false;
    trait_contents(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // type_method | method
  public static boolean trait_method(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_method")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TRAIT_METHOD, "<trait method>");
    r = type_method(b, l + 1);
    if (!r) r = method(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER COLONCOLON? path_generic_args_without_colons
  public static boolean trait_ref(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_ref")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && trait_ref_1(b, l + 1);
    r = r && path_generic_args_without_colons(b, l + 1);
    exit_section_(b, m, TRAIT_REF, r);
    return r;
  }

  // COLONCOLON?
  private static boolean trait_ref_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_ref_1")) return false;
    consumeToken(b, COLONCOLON);
    return true;
  }

  /* ********************************************************** */
  // outer_attrs? TYPE type_param SEMICOLON
  public static boolean trait_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_type")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TRAIT_TYPE, "<trait type>");
    r = trait_type_0(b, l + 1);
    r = r && consumeToken(b, TYPE);
    r = r && type_param(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // outer_attrs?
  private static boolean trait_type_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_type_0")) return false;
    outer_attrs(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // type_prim
  //                | type_closure
  //                | LT type_sum as_trait_ref? GT COLONCOLON IDENTIFIER
  //                | LPAREN [ type_sums COMMA? ] RPAREN
  static boolean type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_prim(b, l + 1);
    if (!r) r = type_closure(b, l + 1);
    if (!r) r = type_2(b, l + 1);
    if (!r) r = type_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LT type_sum as_trait_ref? GT COLONCOLON IDENTIFIER
  private static boolean type_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && type_sum(b, l + 1);
    r = r && type_2_2(b, l + 1);
    r = r && consumeTokens(b, 0, GT, COLONCOLON, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  // as_trait_ref?
  private static boolean type_2_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_2_2")) return false;
    as_trait_ref(b, l + 1);
    return true;
  }

  // LPAREN [ type_sums COMMA? ] RPAREN
  private static boolean type_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && type_3_1(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ type_sums COMMA? ]
  private static boolean type_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_3_1")) return false;
    type_3_1_0(b, l + 1);
    return true;
  }

  // type_sums COMMA?
  private static boolean type_3_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_3_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_sums(b, l + 1);
    r = r && type_3_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean type_3_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_3_1_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // COLON type_sum
  public static boolean type_ascription(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_ascription")) return false;
    if (!nextTokenIs(b, COLON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && type_sum(b, l + 1);
    exit_section_(b, m, TYPE_ASCRIPTION, r);
    return r;
  }

  /* ********************************************************** */
  // [ UNSAFE? [ EXTERN abi? ] ] FN type_fn_decl
  static boolean type_bare_fn(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_bare_fn")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_bare_fn_0(b, l + 1);
    r = r && consumeToken(b, FN);
    r = r && type_fn_decl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ UNSAFE? [ EXTERN abi? ] ]
  private static boolean type_bare_fn_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_bare_fn_0")) return false;
    type_bare_fn_0_0(b, l + 1);
    return true;
  }

  // UNSAFE? [ EXTERN abi? ]
  private static boolean type_bare_fn_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_bare_fn_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_bare_fn_0_0_0(b, l + 1);
    r = r && type_bare_fn_0_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // UNSAFE?
  private static boolean type_bare_fn_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_bare_fn_0_0_0")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  // [ EXTERN abi? ]
  private static boolean type_bare_fn_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_bare_fn_0_0_1")) return false;
    type_bare_fn_0_0_1_0(b, l + 1);
    return true;
  }

  // EXTERN abi?
  private static boolean type_bare_fn_0_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_bare_fn_0_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXTERN);
    r = r && type_bare_fn_0_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // abi?
  private static boolean type_bare_fn_0_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_bare_fn_0_0_1_0_1")) return false;
    abi(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // UNSAFE OR anon_params OR bounds? ret_type
  //                        |        OR anon_params OR bounds? ret_type
  //                        | UNSAFE OROR              bounds? ret_type
  //                        |        OROR              bounds? ret_type
  static boolean type_closure(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_closure_0(b, l + 1);
    if (!r) r = type_closure_1(b, l + 1);
    if (!r) r = type_closure_2(b, l + 1);
    if (!r) r = type_closure_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // UNSAFE OR anon_params OR bounds? ret_type
  private static boolean type_closure_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, UNSAFE, OR);
    r = r && anon_params(b, l + 1);
    r = r && consumeToken(b, OR);
    r = r && type_closure_0_4(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // bounds?
  private static boolean type_closure_0_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure_0_4")) return false;
    bounds(b, l + 1);
    return true;
  }

  // OR anon_params OR bounds? ret_type
  private static boolean type_closure_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OR);
    r = r && anon_params(b, l + 1);
    r = r && consumeToken(b, OR);
    r = r && type_closure_1_3(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // bounds?
  private static boolean type_closure_1_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure_1_3")) return false;
    bounds(b, l + 1);
    return true;
  }

  // UNSAFE OROR              bounds? ret_type
  private static boolean type_closure_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, UNSAFE, OROR);
    r = r && type_closure_2_2(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // bounds?
  private static boolean type_closure_2_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure_2_2")) return false;
    bounds(b, l + 1);
    return true;
  }

  // OROR              bounds? ret_type
  private static boolean type_closure_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OROR);
    r = r && type_closure_3_1(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // bounds?
  private static boolean type_closure_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_closure_3_1")) return false;
    bounds(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // generic_params fn_anon_params ret_type
  static boolean type_fn_decl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_fn_decl")) return false;
    if (!nextTokenIs(b, "", LPAREN, LT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = generic_params(b, l + 1);
    r = r && fn_anon_params(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // TYPE IDENTIFIER generic_params where_clause? EQ type_sum SEMICOLON
  public static boolean type_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_item")) return false;
    if (!nextTokenIs(b, TYPE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, TYPE, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && type_item_3(b, l + 1);
    r = r && consumeToken(b, EQ);
    r = r && type_sum(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, TYPE_ITEM, r);
    return r;
  }

  // where_clause?
  private static boolean type_item_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_item_3")) return false;
    where_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // attrs_and_vis UNSAFE? [ EXTERN abi? ] FN IDENTIFIER generic_params fn_decl_with_self_allow_anon_params where_clause? SEMICOLON
  public static boolean type_method(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_method")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_METHOD, "<type method>");
    r = attrs_and_vis(b, l + 1);
    r = r && type_method_1(b, l + 1);
    r = r && type_method_2(b, l + 1);
    r = r && consumeTokens(b, 0, FN, IDENTIFIER);
    r = r && generic_params(b, l + 1);
    r = r && fn_decl_with_self_allow_anon_params(b, l + 1);
    r = r && type_method_7(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // UNSAFE?
  private static boolean type_method_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_method_1")) return false;
    consumeToken(b, UNSAFE);
    return true;
  }

  // [ EXTERN abi? ]
  private static boolean type_method_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_method_2")) return false;
    type_method_2_0(b, l + 1);
    return true;
  }

  // EXTERN abi?
  private static boolean type_method_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_method_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXTERN);
    r = r && type_method_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // abi?
  private static boolean type_method_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_method_2_0_1")) return false;
    abi(b, l + 1);
    return true;
  }

  // where_clause?
  private static boolean type_method_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_method_7")) return false;
    where_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // [ IDENTIFIER Q ] IDENTIFIER type_param_bounds? type_param_default?
  public static boolean type_param(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_param_0(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    r = r && type_param_2(b, l + 1);
    r = r && type_param_3(b, l + 1);
    exit_section_(b, m, TYPE_PARAM, r);
    return r;
  }

  // [ IDENTIFIER Q ]
  private static boolean type_param_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_0")) return false;
    parseTokens(b, 0, IDENTIFIER, Q);
    return true;
  }

  // type_param_bounds?
  private static boolean type_param_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_2")) return false;
    type_param_bounds(b, l + 1);
    return true;
  }

  // type_param_default?
  private static boolean type_param_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_3")) return false;
    type_param_default(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // polybound type_param_bound_seq_right
  static boolean type_param_bound_seq(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_bound_seq")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = polybound(b, l + 1);
    r = r && type_param_bound_seq_right(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // [ PLUS polybound type_param_bound_seq_right ]
  static boolean type_param_bound_seq_right(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_bound_seq_right")) return false;
    type_param_bound_seq_right_0(b, l + 1);
    return true;
  }

  // PLUS polybound type_param_bound_seq_right
  private static boolean type_param_bound_seq_right_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_bound_seq_right_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PLUS);
    r = r && polybound(b, l + 1);
    r = r && type_param_bound_seq_right(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // COLON [ type_param_bound_seq ]
  public static boolean type_param_bounds(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_bounds")) return false;
    if (!nextTokenIs(b, COLON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && type_param_bounds_1(b, l + 1);
    exit_section_(b, m, TYPE_PARAM_BOUNDS, r);
    return r;
  }

  // [ type_param_bound_seq ]
  private static boolean type_param_bounds_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_bounds_1")) return false;
    type_param_bound_seq(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // EQ type_sum
  public static boolean type_param_default(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_param_default")) return false;
    if (!nextTokenIs(b, EQ)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EQ);
    r = r && type_sum(b, l + 1);
    exit_section_(b, m, TYPE_PARAM_DEFAULT, r);
    return r;
  }

  /* ********************************************************** */
  // type_param (COMMA type_params)*
  static boolean type_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_params")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_param(b, l + 1);
    r = r && type_params_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA type_params)*
  private static boolean type_params_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_params_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!type_params_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "type_params_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA type_params
  private static boolean type_params_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_params_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && type_params(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // [ SELF? COLONCOLON ] path_generic_args_without_colons
  //                     | BOX type
  //                     | MUL    [ MUT | CONST ] type
  //                     | AND    [ lifetime? MUT ] type
  //                     | ANDAND [ lifetime? MUT ] type
  //                     | LBRACK type [ (COMMA DOTDOT | SEMICOLON) expr ] RBRACK
  //                     | TYPEOF LPAREN expr RPAREN
  //                     | UNDERSCORE
  //                     | type_bare_fn
  //                     | type_proc
  //                     | for_in_type
  static boolean type_prim(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_prim_0(b, l + 1);
    if (!r) r = type_prim_1(b, l + 1);
    if (!r) r = type_prim_2(b, l + 1);
    if (!r) r = type_prim_3(b, l + 1);
    if (!r) r = type_prim_4(b, l + 1);
    if (!r) r = type_prim_5(b, l + 1);
    if (!r) r = type_prim_6(b, l + 1);
    if (!r) r = consumeToken(b, UNDERSCORE);
    if (!r) r = type_bare_fn(b, l + 1);
    if (!r) r = type_proc(b, l + 1);
    if (!r) r = for_in_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ SELF? COLONCOLON ] path_generic_args_without_colons
  private static boolean type_prim_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_prim_0_0(b, l + 1);
    r = r && path_generic_args_without_colons(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ SELF? COLONCOLON ]
  private static boolean type_prim_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_0_0")) return false;
    type_prim_0_0_0(b, l + 1);
    return true;
  }

  // SELF? COLONCOLON
  private static boolean type_prim_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_0_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_prim_0_0_0_0(b, l + 1);
    r = r && consumeToken(b, COLONCOLON);
    exit_section_(b, m, null, r);
    return r;
  }

  // SELF?
  private static boolean type_prim_0_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_0_0_0_0")) return false;
    consumeToken(b, SELF);
    return true;
  }

  // BOX type
  private static boolean type_prim_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BOX);
    r = r && type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // MUL    [ MUT | CONST ] type
  private static boolean type_prim_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, MUL);
    r = r && type_prim_2_1(b, l + 1);
    r = r && type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ MUT | CONST ]
  private static boolean type_prim_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_2_1")) return false;
    type_prim_2_1_0(b, l + 1);
    return true;
  }

  // MUT | CONST
  private static boolean type_prim_2_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_2_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, MUT);
    if (!r) r = consumeToken(b, CONST);
    exit_section_(b, m, null, r);
    return r;
  }

  // AND    [ lifetime? MUT ] type
  private static boolean type_prim_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AND);
    r = r && type_prim_3_1(b, l + 1);
    r = r && type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ lifetime? MUT ]
  private static boolean type_prim_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_3_1")) return false;
    type_prim_3_1_0(b, l + 1);
    return true;
  }

  // lifetime? MUT
  private static boolean type_prim_3_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_3_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_prim_3_1_0_0(b, l + 1);
    r = r && consumeToken(b, MUT);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetime?
  private static boolean type_prim_3_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_3_1_0_0")) return false;
    lifetime(b, l + 1);
    return true;
  }

  // ANDAND [ lifetime? MUT ] type
  private static boolean type_prim_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ANDAND);
    r = r && type_prim_4_1(b, l + 1);
    r = r && type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ lifetime? MUT ]
  private static boolean type_prim_4_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_4_1")) return false;
    type_prim_4_1_0(b, l + 1);
    return true;
  }

  // lifetime? MUT
  private static boolean type_prim_4_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_4_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_prim_4_1_0_0(b, l + 1);
    r = r && consumeToken(b, MUT);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetime?
  private static boolean type_prim_4_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_4_1_0_0")) return false;
    lifetime(b, l + 1);
    return true;
  }

  // LBRACK type [ (COMMA DOTDOT | SEMICOLON) expr ] RBRACK
  private static boolean type_prim_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACK);
    r = r && type(b, l + 1);
    r = r && type_prim_5_2(b, l + 1);
    r = r && consumeToken(b, RBRACK);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ (COMMA DOTDOT | SEMICOLON) expr ]
  private static boolean type_prim_5_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_5_2")) return false;
    type_prim_5_2_0(b, l + 1);
    return true;
  }

  // (COMMA DOTDOT | SEMICOLON) expr
  private static boolean type_prim_5_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_5_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_prim_5_2_0_0(b, l + 1);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA DOTDOT | SEMICOLON
  private static boolean type_prim_5_2_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_5_2_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parseTokens(b, 0, COMMA, DOTDOT);
    if (!r) r = consumeToken(b, SEMICOLON);
    exit_section_(b, m, null, r);
    return r;
  }

  // TYPEOF LPAREN expr RPAREN
  private static boolean type_prim_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_6")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, TYPEOF, LPAREN);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // type_prim [ PLUS type_param_bounds ]
  public static boolean type_prim_sum(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_sum")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_PRIM_SUM, "<type prim sum>");
    r = type_prim(b, l + 1);
    r = r && type_prim_sum_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ PLUS type_param_bounds ]
  private static boolean type_prim_sum_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_sum_1")) return false;
    type_prim_sum_1_0(b, l + 1);
    return true;
  }

  // PLUS type_param_bounds
  private static boolean type_prim_sum_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_prim_sum_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PLUS);
    r = r && type_param_bounds(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // PROC generic_params fn_params bounds? ret_type
  static boolean type_proc(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_proc")) return false;
    if (!nextTokenIs(b, PROC)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PROC);
    r = r && generic_params(b, l + 1);
    r = r && fn_params(b, l + 1);
    r = r && type_proc_3(b, l + 1);
    r = r && ret_type(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // bounds?
  private static boolean type_proc_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_proc_3")) return false;
    bounds(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // type [ PLUS type_param_bounds ]
  public static boolean type_sum(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sum")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, TYPE_SUM, "<type sum>");
    r = type(b, l + 1);
    r = r && type_sum_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ PLUS type_param_bounds ]
  private static boolean type_sum_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sum_1")) return false;
    type_sum_1_0(b, l + 1);
    return true;
  }

  // PLUS type_param_bounds
  private static boolean type_sum_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sum_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PLUS);
    r = r && type_param_bounds(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // type_sum (COMMA type_sum)*
  public static boolean type_sums(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_SUMS, "<type sums>");
    r = type_sum(b, l + 1);
    r = r && type_sums_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (COMMA type_sum)*
  private static boolean type_sums_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!type_sums_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "type_sums_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA type_sum
  private static boolean type_sums_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && type_sum(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // type_sums [ COMMA bindings ]
  //                                     |                   bindings COMMA?
  static boolean type_sums_and_or_bindings(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums_and_or_bindings")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_sums_and_or_bindings_0(b, l + 1);
    if (!r) r = type_sums_and_or_bindings_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type_sums [ COMMA bindings ]
  private static boolean type_sums_and_or_bindings_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums_and_or_bindings_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type_sums(b, l + 1);
    r = r && type_sums_and_or_bindings_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ COMMA bindings ]
  private static boolean type_sums_and_or_bindings_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums_and_or_bindings_0_1")) return false;
    type_sums_and_or_bindings_0_1_0(b, l + 1);
    return true;
  }

  // COMMA bindings
  private static boolean type_sums_and_or_bindings_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums_and_or_bindings_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && bindings(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // bindings COMMA?
  private static boolean type_sums_and_or_bindings_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums_and_or_bindings_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = bindings(b, l + 1);
    r = r && type_sums_and_or_bindings_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean type_sums_and_or_bindings_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_sums_and_or_bindings_1_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // UNSAFE fn_item
  static boolean unsafe_fn_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unsafe_fn_item")) return false;
    if (!nextTokenIs(b, UNSAFE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, UNSAFE);
    r = r && fn_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // USE view_path SEMICOLON
  public static boolean use_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "use_item")) return false;
    if (!nextTokenIs(b, USE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, USE_ITEM, null);
    r = consumeToken(b, USE);
    r = r && view_path(b, l + 1);
    p = r; // pin = 2
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // use_item
  //                     | extern_fn_item
  //                     | extern_crate_decl
  static boolean view_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_item")) return false;
    if (!nextTokenIs(b, "", EXTERN, USE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = use_item(b, l + 1);
    if (!r) r = extern_fn_item(b, l + 1);
    if (!r) r = extern_crate_decl(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // path_no_types_allowed? COLONCOLON LBRACE [ idents_or_self COMMA? ] RBRACE
  //             | path_no_types_allowed  COLONCOLON MUL
  //             |                                   LBRACE [ idents_or_self COMMA? ] RBRACE
  //             | path_no_types_allowed AS IDENTIFIER
  //             | path_no_types_allowed
  public static boolean view_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VIEW_PATH, "<view path>");
    r = view_path_0(b, l + 1);
    if (!r) r = view_path_1(b, l + 1);
    if (!r) r = view_path_2(b, l + 1);
    if (!r) r = view_path_3(b, l + 1);
    if (!r) r = path_no_types_allowed(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // path_no_types_allowed? COLONCOLON LBRACE [ idents_or_self COMMA? ] RBRACE
  private static boolean view_path_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = view_path_0_0(b, l + 1);
    r = r && consumeTokens(b, 0, COLONCOLON, LBRACE);
    r = r && view_path_0_3(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // path_no_types_allowed?
  private static boolean view_path_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_0_0")) return false;
    path_no_types_allowed(b, l + 1);
    return true;
  }

  // [ idents_or_self COMMA? ]
  private static boolean view_path_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_0_3")) return false;
    view_path_0_3_0(b, l + 1);
    return true;
  }

  // idents_or_self COMMA?
  private static boolean view_path_0_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_0_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = idents_or_self(b, l + 1);
    r = r && view_path_0_3_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean view_path_0_3_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_0_3_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  // path_no_types_allowed  COLONCOLON MUL
  private static boolean view_path_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_no_types_allowed(b, l + 1);
    r = r && consumeTokens(b, 0, COLONCOLON, MUL);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE [ idents_or_self COMMA? ] RBRACE
  private static boolean view_path_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && view_path_2_1(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ idents_or_self COMMA? ]
  private static boolean view_path_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_2_1")) return false;
    view_path_2_1_0(b, l + 1);
    return true;
  }

  // idents_or_self COMMA?
  private static boolean view_path_2_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_2_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = idents_or_self(b, l + 1);
    r = r && view_path_2_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA?
  private static boolean view_path_2_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_2_1_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  // path_no_types_allowed AS IDENTIFIER
  private static boolean view_path_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_no_types_allowed(b, l + 1);
    r = r && consumeTokens(b, 0, AS, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // COLONCOLON IDENTIFIER
  public static boolean view_path_part(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_part")) return false;
    if (!nextTokenIs(b, COLONCOLON)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, VIEW_PATH_PART, null);
    r = consumeTokens(b, 0, COLONCOLON, IDENTIFIER);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // COLONCOLON? (SELF | IDENTIFIER)
  public static boolean view_path_part_leftish(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_part_leftish")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VIEW_PATH_PART_LEFTISH, "<view path part leftish>");
    r = view_path_part_leftish_0(b, l + 1);
    r = r && view_path_part_leftish_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // COLONCOLON?
  private static boolean view_path_part_leftish_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_part_leftish_0")) return false;
    consumeToken(b, COLONCOLON);
    return true;
  }

  // SELF | IDENTIFIER
  private static boolean view_path_part_leftish_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_path_part_leftish_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SELF);
    if (!r) r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // PUB?
  static boolean vis(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "vis")) return false;
    consumeToken(b, PUB);
    return true;
  }

  /* ********************************************************** */
  // WHERE where_preds COMMA?
  public static boolean where_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_clause")) return false;
    if (!nextTokenIs(b, WHERE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, WHERE);
    r = r && where_preds(b, l + 1);
    r = r && where_clause_2(b, l + 1);
    exit_section_(b, m, WHERE_CLAUSE, r);
    return r;
  }

  // COMMA?
  private static boolean where_clause_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_clause_2")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // for_lifetimes? (lifetime COLON bounds | type COLON type_param_bounds)
  public static boolean where_pred(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_pred")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, WHERE_PRED, "<where pred>");
    r = where_pred_0(b, l + 1);
    r = r && where_pred_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // for_lifetimes?
  private static boolean where_pred_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_pred_0")) return false;
    for_lifetimes(b, l + 1);
    return true;
  }

  // lifetime COLON bounds | type COLON type_param_bounds
  private static boolean where_pred_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_pred_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = where_pred_1_0(b, l + 1);
    if (!r) r = where_pred_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetime COLON bounds
  private static boolean where_pred_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_pred_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lifetime(b, l + 1);
    r = r && consumeToken(b, COLON);
    r = r && bounds(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // type COLON type_param_bounds
  private static boolean where_pred_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_pred_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = type(b, l + 1);
    r = r && consumeToken(b, COLON);
    r = r && type_param_bounds(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // where_pred (COMMA where_pred)*
  static boolean where_preds(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_preds")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = where_pred(b, l + 1);
    r = r && where_preds_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA where_pred)*
  private static boolean where_preds_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_preds_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!where_preds_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "where_preds_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA where_pred
  private static boolean where_preds_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "where_preds_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && where_pred(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // Expression root: expr
  // Operator priority table:
  // 0: ATOM(ret_expr)
  // 1: BINARY(assign_bin_expr)
  // 2: ATOM(while_expr)
  // 3: ATOM(loop_expr)
  // 4: ATOM(cont_expr)
  // 5: ATOM(break_expr)
  // 6: ATOM(for_expr)
  // 7: ATOM(if_expr)
  // 8: ATOM(match_expr)
  // 9: ATOM(if_let_expr)
  // 10: PREFIX(while_let_expr)
  // 11: ATOM(block_expr)
  // 12: ATOM(struct_expr)
  // 13: PREFIX(lambda_expr)
  // 14: POSTFIX(full_range_expr)
  // 15: ATOM(open_range_expr)
  // 16: BINARY(bool_or_bin_expr)
  // 17: BINARY(bool_and_bin_expr)
  // 18: BINARY(bit_or_bin_expr)
  // 19: BINARY(bit_xor_bin_expr)
  // 20: BINARY(bit_and_bin_expr)
  // 21: BINARY(comp_bin_expr)
  // 22: BINARY(rel_comp_bin_expr)
  // 23: BINARY(bit_shift_bin_expr)
  // 24: BINARY(add_bin_expr)
  // 25: BINARY(mul_bin_expr)
  // 26: ATOM(lit_expr) ATOM(path_expr) ATOM(self_expr) POSTFIX(field_expr) POSTFIX(method_call_expr) BINARY(index_expr) POSTFIX(call_expr) ATOM(array_expr) ATOM(tuple_expr) ATOM(unit_expr) ATOM(paren_expr) PREFIX(unary_expr)
  public static boolean expr(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr")) return false;
    addVariant(b, "<expr>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expr>");
    r = ret_expr(b, l + 1);
    if (!r) r = while_expr(b, l + 1);
    if (!r) r = loop_expr(b, l + 1);
    if (!r) r = cont_expr(b, l + 1);
    if (!r) r = break_expr(b, l + 1);
    if (!r) r = for_expr(b, l + 1);
    if (!r) r = if_expr(b, l + 1);
    if (!r) r = match_expr(b, l + 1);
    if (!r) r = if_let_expr(b, l + 1);
    if (!r) r = while_let_expr(b, l + 1);
    if (!r) r = block_expr(b, l + 1);
    if (!r) r = struct_expr(b, l + 1);
    if (!r) r = lambda_expr(b, l + 1);
    if (!r) r = open_range_expr(b, l + 1);
    if (!r) r = lit_expr(b, l + 1);
    if (!r) r = path_expr(b, l + 1);
    if (!r) r = self_expr(b, l + 1);
    if (!r) r = array_expr(b, l + 1);
    if (!r) r = tuple_expr(b, l + 1);
    if (!r) r = unit_expr(b, l + 1);
    if (!r) r = paren_expr(b, l + 1);
    if (!r) r = unary_expr(b, l + 1);
    p = r;
    r = r && expr_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expr_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 1 && assign_bin_expr_0(b, l + 1)) {
        r = expr(b, l, 0);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 14 && full_range_expr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, RANGE_EXPR, r, true, null);
      }
      else if (g < 16 && consumeTokenSmart(b, OROR)) {
        r = expr(b, l, 16);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 17 && consumeTokenSmart(b, ANDAND)) {
        r = expr(b, l, 17);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 18 && consumeTokenSmart(b, OR)) {
        r = expr(b, l, 18);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 19 && consumeTokenSmart(b, XOR)) {
        r = expr(b, l, 19);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 20 && consumeTokenSmart(b, AND)) {
        r = expr(b, l, 20);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 21 && comp_bin_expr_0(b, l + 1)) {
        r = expr(b, l, 21);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 22 && rel_comp_bin_expr_0(b, l + 1)) {
        r = expr(b, l, 22);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 23 && bit_shift_bin_expr_0(b, l + 1)) {
        r = expr(b, l, 23);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 24 && add_bin_expr_0(b, l + 1)) {
        r = expr(b, l, 24);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 25 && mul_bin_expr_0(b, l + 1)) {
        r = expr(b, l, 25);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 26 && field_expr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, FIELD_EXPR, r, true, null);
      }
      else if (g < 26 && method_call_expr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, METHOD_CALL_EXPR, r, true, null);
      }
      else if (g < 26 && consumeTokenSmart(b, LBRACK)) {
        r = report_error_(b, expr(b, l, 26));
        r = consumeToken(b, RBRACK) && r;
        exit_section_(b, l, m, INDEX_EXPR, r, true, null);
      }
      else if (g < 26 && arg_list(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, CALL_EXPR, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // RETURN expr?
  public static boolean ret_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ret_expr")) return false;
    if (!nextTokenIsFast(b, RETURN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, RETURN);
    r = r && ret_expr_1(b, l + 1);
    exit_section_(b, m, RET_EXPR, r);
    return r;
  }

  // expr?
  private static boolean ret_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ret_expr_1")) return false;
    expr(b, l + 1, -1);
    return true;
  }

  // GTGTEQ
  //                   | LTLTEQ
  //                   | OREQ
  //                   | XOREQ
  //                   | ANDEQ
  //                   | EQ
  //                   | PLUSEQ
  //                   | MINUSEQ
  //                   | MULEQ
  //                   | DIVEQ
  //                   | REMEQ
  private static boolean assign_bin_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assign_bin_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, GTGTEQ);
    if (!r) r = consumeTokenSmart(b, LTLTEQ);
    if (!r) r = consumeTokenSmart(b, OREQ);
    if (!r) r = consumeTokenSmart(b, XOREQ);
    if (!r) r = consumeTokenSmart(b, ANDEQ);
    if (!r) r = consumeTokenSmart(b, EQ);
    if (!r) r = consumeTokenSmart(b, PLUSEQ);
    if (!r) r = consumeTokenSmart(b, MINUSEQ);
    if (!r) r = consumeTokenSmart(b, MULEQ);
    if (!r) r = consumeTokenSmart(b, DIVEQ);
    if (!r) r = consumeTokenSmart(b, REMEQ);
    exit_section_(b, m, null, r);
    return r;
  }

  // lifetime COLON WHILE no_struct_lit_expr LBRACE block RBRACE
  public static boolean while_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_expr")) return false;
    if (!nextTokenIsFast(b, LIFETIME, STATIC_LIFETIME)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, WHILE_EXPR, "<while expr>");
    r = lifetime(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, WHILE);
    r = r && no_struct_lit_expr(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // lifetime COLON LOOP LBRACE block RBRACE
  public static boolean loop_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "loop_expr")) return false;
    if (!nextTokenIsFast(b, LIFETIME, STATIC_LIFETIME)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LOOP_EXPR, "<loop expr>");
    r = lifetime(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, LOOP, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // CONTINUE lifetime
  public static boolean cont_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "cont_expr")) return false;
    if (!nextTokenIsFast(b, CONTINUE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, CONTINUE);
    r = r && lifetime(b, l + 1);
    exit_section_(b, m, CONT_EXPR, r);
    return r;
  }

  // BREAK lifetime
  public static boolean break_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "break_expr")) return false;
    if (!nextTokenIsFast(b, BREAK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, BREAK);
    r = r && lifetime(b, l + 1);
    exit_section_(b, m, BREAK_EXPR, r);
    return r;
  }

  // lifetime COLON FOR pat IN no_struct_lit_expr LBRACE block RBRACE
  public static boolean for_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_expr")) return false;
    if (!nextTokenIsFast(b, LIFETIME, STATIC_LIFETIME)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FOR_EXPR, "<for expr>");
    r = lifetime(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, FOR);
    r = r && pat(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && no_struct_lit_expr(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // IF no_struct_lit_expr LBRACE block RBRACE else_tail?
  public static boolean if_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_expr")) return false;
    if (!nextTokenIsFast(b, IF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, IF);
    r = r && no_struct_lit_expr(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    r = r && if_expr_5(b, l + 1);
    exit_section_(b, m, IF_EXPR, r);
    return r;
  }

  // else_tail?
  private static boolean if_expr_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_expr_5")) return false;
    else_tail(b, l + 1);
    return true;
  }

  // MATCH no_struct_lit_expr LBRACE match_arm* RBRACE
  public static boolean match_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_expr")) return false;
    if (!nextTokenIsFast(b, MATCH)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, MATCH);
    r = r && no_struct_lit_expr(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && match_expr_3(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, MATCH_EXPR, r);
    return r;
  }

  // match_arm*
  private static boolean match_expr_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_expr_3")) return false;
    int c = current_position_(b);
    while (true) {
      if (!match_arm(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "match_expr_3", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // IF LET pat EQ expr LBRACE block RBRACE else_tail?
  public static boolean if_let_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_let_expr")) return false;
    if (!nextTokenIsFast(b, IF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IF, LET);
    r = r && pat(b, l + 1);
    r = r && consumeToken(b, EQ);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    r = r && if_let_expr_8(b, l + 1);
    exit_section_(b, m, IF_LET_EXPR, r);
    return r;
  }

  // else_tail?
  private static boolean if_let_expr_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_let_expr_8")) return false;
    else_tail(b, l + 1);
    return true;
  }

  public static boolean while_let_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_let_expr")) return false;
    if (!nextTokenIsFast(b, WHILE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = while_let_expr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, 10);
    r = p && report_error_(b, while_let_expr_1(b, l + 1)) && r;
    exit_section_(b, l, m, WHILE_LET_EXPR, r, p, null);
    return r || p;
  }

  // WHILE LET pat EQ
  private static boolean while_let_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_let_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, WHILE, LET);
    r = r && pat(b, l + 1);
    r = r && consumeToken(b, EQ);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE block RBRACE
  private static boolean while_let_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_let_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE (stmt SEMICOLON | item)* (expr) RBRACE
  public static boolean block_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_expr")) return false;
    if (!nextTokenIsFast(b, LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, BLOCK_EXPR, null);
    r = consumeTokenSmart(b, LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, block_expr_1(b, l + 1));
    r = p && report_error_(b, block_expr_2(b, l + 1)) && r;
    r = p && consumeToken(b, RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (stmt SEMICOLON | item)*
  private static boolean block_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_expr_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!block_expr_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "block_expr_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // stmt SEMICOLON | item
  private static boolean block_expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_expr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_expr_1_0_0(b, l + 1);
    if (!r) r = item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // stmt SEMICOLON
  private static boolean block_expr_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_expr_1_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = stmt(b, l + 1);
    p = r; // pin = 1
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (expr)
  private static boolean block_expr_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_expr_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // path_expr LBRACE  IDENTIFIER COLON expr
  //                          (COMMA   IDENTIFIER COLON expr)*
  //                          (DOTDOT  expr)? RBRACE
  //               | path_expr LPAREN expr
  //                          (COMMA  expr)* RPAREN
  //               | path_expr
  public static boolean struct_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, STRUCT_EXPR, "<struct expr>");
    r = struct_expr_0(b, l + 1);
    if (!r) r = struct_expr_1(b, l + 1);
    if (!r) r = path_expr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // path_expr LBRACE  IDENTIFIER COLON expr
  //                          (COMMA   IDENTIFIER COLON expr)*
  //                          (DOTDOT  expr)? RBRACE
  private static boolean struct_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_expr(b, l + 1);
    r = r && consumeTokens(b, 0, LBRACE, IDENTIFIER, COLON);
    r = r && expr(b, l + 1, -1);
    r = r && struct_expr_0_5(b, l + 1);
    r = r && struct_expr_0_6(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA   IDENTIFIER COLON expr)*
  private static boolean struct_expr_0_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_0_5")) return false;
    int c = current_position_(b);
    while (true) {
      if (!struct_expr_0_5_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "struct_expr_0_5", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA   IDENTIFIER COLON expr
  private static boolean struct_expr_0_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_0_5_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COMMA, IDENTIFIER, COLON);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (DOTDOT  expr)?
  private static boolean struct_expr_0_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_0_6")) return false;
    struct_expr_0_6_0(b, l + 1);
    return true;
  }

  // DOTDOT  expr
  private static boolean struct_expr_0_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_0_6_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, DOTDOT);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // path_expr LPAREN expr
  //                          (COMMA  expr)* RPAREN
  private static boolean struct_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_expr(b, l + 1);
    r = r && consumeToken(b, LPAREN);
    r = r && expr(b, l + 1, -1);
    r = r && struct_expr_1_3(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA  expr)*
  private static boolean struct_expr_1_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_1_3")) return false;
    int c = current_position_(b);
    while (true) {
      if (!struct_expr_1_3_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "struct_expr_1_3", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA  expr
  private static boolean struct_expr_1_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_1_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean lambda_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambda_expr")) return false;
    if (!nextTokenIsFast(b, OR)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = lambda_expr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, 13);
    exit_section_(b, l, m, LAMBDA_EXPR, r, p, null);
    return r || p;
  }

  // OR ident_list OR
  private static boolean lambda_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambda_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, OR);
    r = r && ident_list(b, l + 1);
    r = r && consumeToken(b, OR);
    exit_section_(b, m, null, r);
    return r;
  }

  // DOTDOT expr?
  private static boolean full_range_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "full_range_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, DOTDOT);
    r = r && full_range_expr_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // expr?
  private static boolean full_range_expr_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "full_range_expr_0_1")) return false;
    expr(b, l + 1, -1);
    return true;
  }

  // DOTDOT expr?
  public static boolean open_range_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "open_range_expr")) return false;
    if (!nextTokenIsFast(b, DOTDOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, DOTDOT);
    r = r && open_range_expr_1(b, l + 1);
    exit_section_(b, m, RANGE_EXPR, r);
    return r;
  }

  // expr?
  private static boolean open_range_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "open_range_expr_1")) return false;
    expr(b, l + 1, -1);
    return true;
  }

  // EQEQ | EXCLEQ
  private static boolean comp_bin_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comp_bin_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, EQEQ);
    if (!r) r = consumeTokenSmart(b, EXCLEQ);
    exit_section_(b, m, null, r);
    return r;
  }

  // LT | GT | LTEQ | GTEQ
  private static boolean rel_comp_bin_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "rel_comp_bin_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LT);
    if (!r) r = consumeTokenSmart(b, GT);
    if (!r) r = consumeTokenSmart(b, LTEQ);
    if (!r) r = consumeTokenSmart(b, GTEQ);
    exit_section_(b, m, null, r);
    return r;
  }

  // LTLT | GTGT
  private static boolean bit_shift_bin_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bit_shift_bin_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LTLT);
    if (!r) r = consumeTokenSmart(b, GTGT);
    exit_section_(b, m, null, r);
    return r;
  }

  // PLUS | MINUS
  private static boolean add_bin_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "add_bin_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, PLUS);
    if (!r) r = consumeTokenSmart(b, MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // MUL | DIV | REM
  private static boolean mul_bin_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mul_bin_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, MUL);
    if (!r) r = consumeTokenSmart(b, DIV);
    if (!r) r = consumeTokenSmart(b, REM);
    exit_section_(b, m, null, r);
    return r;
  }

  // lit
  public static boolean lit_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lit_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIT_EXPR, "<lit expr>");
    r = lit(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ SELF? COLONCOLON ] path_generic_args_with_colons
  public static boolean path_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATH_EXPR, "<path expr>");
    r = path_expr_0(b, l + 1);
    r = r && path_generic_args_with_colons(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ SELF? COLONCOLON ]
  private static boolean path_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_expr_0")) return false;
    path_expr_0_0(b, l + 1);
    return true;
  }

  // SELF? COLONCOLON
  private static boolean path_expr_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_expr_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path_expr_0_0_0(b, l + 1);
    r = r && consumeToken(b, COLONCOLON);
    exit_section_(b, m, null, r);
    return r;
  }

  // SELF?
  private static boolean path_expr_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_expr_0_0_0")) return false;
    consumeTokenSmart(b, SELF);
    return true;
  }

  // SELF
  public static boolean self_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "self_expr")) return false;
    if (!nextTokenIsFast(b, SELF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, SELF);
    exit_section_(b, m, SELF_EXPR, r);
    return r;
  }

  // DOT (IDENTIFIER | INTEGER_LITERAL)
  private static boolean field_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, DOT);
    r = r && field_expr_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER | INTEGER_LITERAL
  private static boolean field_expr_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "field_expr_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, IDENTIFIER);
    if (!r) r = consumeTokenSmart(b, INTEGER_LITERAL);
    exit_section_(b, m, null, r);
    return r;
  }

  // DOT IDENTIFIER arg_list
  private static boolean method_call_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_call_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOT, IDENTIFIER);
    r = r && arg_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACK MUT? array_elems? RBRACK
  public static boolean array_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_expr")) return false;
    if (!nextTokenIsFast(b, LBRACK)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ARRAY_EXPR, null);
    r = consumeTokenSmart(b, LBRACK);
    p = r; // pin = 1
    r = r && report_error_(b, array_expr_1(b, l + 1));
    r = p && report_error_(b, array_expr_2(b, l + 1)) && r;
    r = p && consumeToken(b, RBRACK) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // MUT?
  private static boolean array_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_expr_1")) return false;
    consumeTokenSmart(b, MUT);
    return true;
  }

  // array_elems?
  private static boolean array_expr_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_expr_2")) return false;
    array_elems(b, l + 1);
    return true;
  }

  // LPAREN expr ((COMMA expr)+ | COMMA) RPAREN
  public static boolean tuple_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_expr")) return false;
    if (!nextTokenIsFast(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LPAREN);
    r = r && expr(b, l + 1, -1);
    r = r && tuple_expr_2(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, TUPLE_EXPR, r);
    return r;
  }

  // (COMMA expr)+ | COMMA
  private static boolean tuple_expr_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_expr_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tuple_expr_2_0(b, l + 1);
    if (!r) r = consumeTokenSmart(b, COMMA);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA expr)+
  private static boolean tuple_expr_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_expr_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tuple_expr_2_0_0(b, l + 1);
    int c = current_position_(b);
    while (r) {
      if (!tuple_expr_2_0_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "tuple_expr_2_0", c)) break;
      c = current_position_(b);
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA expr
  private static boolean tuple_expr_2_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_expr_2_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN RPAREN
  public static boolean unit_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unit_expr")) return false;
    if (!nextTokenIsFast(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LPAREN, RPAREN);
    exit_section_(b, m, UNIT_EXPR, r);
    return r;
  }

  // LPAREN expr RPAREN
  public static boolean paren_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paren_expr")) return false;
    if (!nextTokenIsFast(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LPAREN);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, PAREN_EXPR, r);
    return r;
  }

  public static boolean unary_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = unary_expr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, -1);
    exit_section_(b, l, m, UNARY_EXPR, r, p, null);
    return r || p;
  }

  // MINUS | MUL | EXCL | AND
  private static boolean unary_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, MINUS);
    if (!r) r = consumeTokenSmart(b, MUL);
    if (!r) r = consumeTokenSmart(b, EXCL);
    if (!r) r = consumeTokenSmart(b, AND);
    exit_section_(b, m, null, r);
    return r;
  }

  final static Parser fn_item_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return fn_item_recover(b, l + 1);
    }
  };
}
