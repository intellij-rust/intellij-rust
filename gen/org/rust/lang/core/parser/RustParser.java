// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static org.rust.lang.core.psi.RustCompositeElementTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
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
    if (t == ARRAY_EXPR) {
      r = array_expr(b, 0);
    }
    else if (t == ATTR) {
      r = attr(b, 0);
    }
    else if (t == BIT_AND_BIN_EXPR) {
      r = expr(b, 0, 4);
    }
    else if (t == BIT_LEFT_SHIFT_BIN_EXPR) {
      r = expr(b, 0, 7);
    }
    else if (t == BIT_OR_BIN_EXPR) {
      r = expr(b, 0, 2);
    }
    else if (t == BIT_RIGHT_SHIFT_BIN_EXPR) {
      r = expr(b, 0, 7);
    }
    else if (t == BIT_XOR_BIN_EXPR) {
      r = expr(b, 0, 3);
    }
    else if (t == BLOCK) {
      r = block(b, 0);
    }
    else if (t == BLOCK_EXPR) {
      r = block_expr(b, 0);
    }
    else if (t == BOOL_AND_BIN_EXPR) {
      r = expr(b, 0, 1);
    }
    else if (t == BOOL_OR_BIN_EXPR) {
      r = expr(b, 0, 0);
    }
    else if (t == BREAK_EXPR) {
      r = break_expr(b, 0);
    }
    else if (t == CALL_EXPR) {
      r = expr(b, 0, 31);
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
    else if (t == DIV_BIN_EXPR) {
      r = expr(b, 0, 9);
    }
    else if (t == ENUM_ITEM) {
      r = enum_item(b, 0);
    }
    else if (t == EQ_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_BIT_AND_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_BIT_GTGT_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_BIT_LTLT_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_BIT_OR_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_BIT_XOR_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_COMP_BIN_EXPR) {
      r = expr(b, 0, 5);
    }
    else if (t == EQ_DIV_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_MINUS_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_MUL_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_PLUS_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EQ_REM_BIN_EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EXPR) {
      r = expr(b, 0, -1);
    }
    else if (t == EXPR_PATH) {
      r = expr_path(b, 0);
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
    else if (t == FIELD_EXPR) {
      r = expr(b, 0, 33);
    }
    else if (t == FN_ITEM) {
      r = fn_item(b, 0);
    }
    else if (t == FOR_EXPR) {
      r = for_expr(b, 0);
    }
    else if (t == FOREIGN_FN_ITEM) {
      r = foreign_fn_item(b, 0);
    }
    else if (t == FULL_OPEN_EXPR) {
      r = full_open_expr(b, 0);
    }
    else if (t == FULL_RANGE_EXPR) {
      r = expr(b, 0, 29);
    }
    else if (t == GT_COMP_BIN_EXPR) {
      r = expr(b, 0, 6);
    }
    else if (t == GTEQ_COMP_BIN_EXPR) {
      r = expr(b, 0, 6);
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
    else if (t == INDEX_EXPR) {
      r = expr(b, 0, 30);
    }
    else if (t == INEQ_COMP_BIN_EXPR) {
      r = expr(b, 0, 5);
    }
    else if (t == ITEM) {
      r = item(b, 0);
    }
    else if (t == LAMBDA_EXPR) {
      r = lambda_expr(b, 0);
    }
    else if (t == LEFT_OPEN_EXPR) {
      r = left_open_expr(b, 0);
    }
    else if (t == LET_DECL) {
      r = let_decl(b, 0);
    }
    else if (t == LITERAL_EXPR) {
      r = literal_expr(b, 0);
    }
    else if (t == LOOP_EXPR) {
      r = loop_expr(b, 0);
    }
    else if (t == LT_COMP_BIN_EXPR) {
      r = expr(b, 0, 6);
    }
    else if (t == LTEQ_COMP_BIN_EXPR) {
      r = expr(b, 0, 6);
    }
    else if (t == MATCH_EXPR) {
      r = match_expr(b, 0);
    }
    else if (t == METHOD_CALL_EXPR) {
      r = expr(b, 0, 32);
    }
    else if (t == MINUS_BIN_EXPR) {
      r = expr(b, 0, 8);
    }
    else if (t == MOD_ITEM) {
      r = mod_item(b, 0);
    }
    else if (t == MUL_BIN_EXPR) {
      r = expr(b, 0, 9);
    }
    else if (t == PAREN_EXPR) {
      r = paren_expr(b, 0);
    }
    else if (t == PATH) {
      r = path(b, 0);
    }
    else if (t == PATH_GLOB) {
      r = path_glob(b, 0);
    }
    else if (t == PLUS_BIN_EXPR) {
      r = expr(b, 0, 8);
    }
    else if (t == REM_BIN_EXPR) {
      r = expr(b, 0, 9);
    }
    else if (t == RET_EXPR) {
      r = ret_expr(b, 0);
    }
    else if (t == RIGHT_OPEN_EXPR) {
      r = expr(b, 0, 29);
    }
    else if (t == STATIC_ITEM) {
      r = static_item(b, 0);
    }
    else if (t == STMT) {
      r = stmt(b, 0);
    }
    else if (t == STRUCT_EXPR) {
      r = struct_expr(b, 0);
    }
    else if (t == STRUCT_ITEM) {
      r = struct_item(b, 0);
    }
    else if (t == TRAIT_ITEM) {
      r = trait_item(b, 0);
    }
    else if (t == TUPLE_EXPR) {
      r = tuple_expr(b, 0);
    }
    else if (t == TYPE_ITEM) {
      r = type_item(b, 0);
    }
    else if (t == UNARY_EXPR) {
      r = unary_expr(b, 0);
    }
    else if (t == UNIT_EXPR) {
      r = unit_expr(b, 0);
    }
    else if (t == USE_DECL) {
      r = use_decl(b, 0);
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
    return file(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(DECL_STMT, EXPR_STMT, STMT),
    create_token_set_(BIT_AND_BIN_EXPR, BIT_LEFT_SHIFT_BIN_EXPR, BIT_OR_BIN_EXPR, BIT_RIGHT_SHIFT_BIN_EXPR,
      BIT_XOR_BIN_EXPR, BOOL_AND_BIN_EXPR, BOOL_OR_BIN_EXPR, DIV_BIN_EXPR,
      EQ_BIN_EXPR, EQ_BIT_AND_BIN_EXPR, EQ_BIT_GTGT_BIN_EXPR, EQ_BIT_LTLT_BIN_EXPR,
      EQ_BIT_OR_BIN_EXPR, EQ_BIT_XOR_BIN_EXPR, EQ_COMP_BIN_EXPR, EQ_DIV_BIN_EXPR,
      EQ_MINUS_BIN_EXPR, EQ_MUL_BIN_EXPR, EQ_PLUS_BIN_EXPR, EQ_REM_BIN_EXPR,
      GTEQ_COMP_BIN_EXPR, GT_COMP_BIN_EXPR, INEQ_COMP_BIN_EXPR, LTEQ_COMP_BIN_EXPR,
      LT_COMP_BIN_EXPR, MINUS_BIN_EXPR, MUL_BIN_EXPR, PLUS_BIN_EXPR,
      REM_BIN_EXPR),
    create_token_set_(ARRAY_EXPR, BIT_AND_BIN_EXPR, BIT_LEFT_SHIFT_BIN_EXPR, BIT_OR_BIN_EXPR,
      BIT_RIGHT_SHIFT_BIN_EXPR, BIT_XOR_BIN_EXPR, BLOCK_EXPR, BOOL_AND_BIN_EXPR,
      BOOL_OR_BIN_EXPR, BREAK_EXPR, CALL_EXPR, CONT_EXPR,
      DIV_BIN_EXPR, EQ_BIN_EXPR, EQ_BIT_AND_BIN_EXPR, EQ_BIT_GTGT_BIN_EXPR,
      EQ_BIT_LTLT_BIN_EXPR, EQ_BIT_OR_BIN_EXPR, EQ_BIT_XOR_BIN_EXPR, EQ_COMP_BIN_EXPR,
      EQ_DIV_BIN_EXPR, EQ_MINUS_BIN_EXPR, EQ_MUL_BIN_EXPR, EQ_PLUS_BIN_EXPR,
      EQ_REM_BIN_EXPR, EXPR, EXPR_PATH, FIELD_EXPR,
      FOR_EXPR, FULL_OPEN_EXPR, FULL_RANGE_EXPR, GTEQ_COMP_BIN_EXPR,
      GT_COMP_BIN_EXPR, IF_EXPR, IF_LET_EXPR, INDEX_EXPR,
      INEQ_COMP_BIN_EXPR, LAMBDA_EXPR, LEFT_OPEN_EXPR, LITERAL_EXPR,
      LOOP_EXPR, LTEQ_COMP_BIN_EXPR, LT_COMP_BIN_EXPR, MATCH_EXPR,
      METHOD_CALL_EXPR, MINUS_BIN_EXPR, MUL_BIN_EXPR, PAREN_EXPR,
      PLUS_BIN_EXPR, REM_BIN_EXPR, RET_EXPR, RIGHT_OPEN_EXPR,
      STRUCT_EXPR, TUPLE_EXPR, UNARY_EXPR, UNIT_EXPR,
      WHILE_EXPR, WHILE_LET_EXPR),
  };

  /* ********************************************************** */
  // pat (OR pat)* (IF expr)?
  static boolean MatchPattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MatchPattern")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pat(b, l + 1);
    r = r && MatchPattern_1(b, l + 1);
    r = r && MatchPattern_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (OR pat)*
  private static boolean MatchPattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MatchPattern_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!MatchPattern_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "MatchPattern_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // OR pat
  private static boolean MatchPattern_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MatchPattern_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OR);
    r = r && pat(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (IF expr)?
  private static boolean MatchPattern_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MatchPattern_2")) return false;
    MatchPattern_2_0(b, l + 1);
    return true;
  }

  // IF expr
  private static boolean MatchPattern_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MatchPattern_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IF);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (expr (COMMA expr)*) | (expr SEMICOLON expr)
  static boolean array_elems(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = array_elems_0(b, l + 1);
    if (!r) r = array_elems_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // expr (COMMA expr)*
  private static boolean array_elems_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr(b, l + 1, -1);
    r = r && array_elems_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA expr)*
  private static boolean array_elems_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!array_elems_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "array_elems_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA expr
  private static boolean array_elems_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // expr SEMICOLON expr
  private static boolean array_elems_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_elems_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr(b, l + 1, -1);
    r = r && consumeToken(b, SEMICOLON);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // SHARP EXCL? LBRACK meta_item RBRACK
  public static boolean attr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attr")) return false;
    if (!nextTokenIs(b, SHARP)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHARP);
    r = r && attr_1(b, l + 1);
    r = r && consumeToken(b, LBRACK);
    r = r && meta_item(b, l + 1);
    r = r && consumeToken(b, RBRACK);
    exit_section_(b, m, ATTR, r);
    return r;
  }

  // EXCL?
  private static boolean attr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "attr_1")) return false;
    consumeToken(b, EXCL);
    return true;
  }

  /* ********************************************************** */
  // LBRACE stmt* expr? RBRACE
  public static boolean block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeToken(b, LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, block_1(b, l + 1));
    r = p && report_error_(b, block_2(b, l + 1)) && r;
    r = p && consumeToken(b, RBRACE) && r;
    exit_section_(b, l, m, BLOCK, r, p, null);
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
  // FALSE | TRUE
  static boolean bool_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bool_literal")) return false;
    if (!nextTokenIs(b, "", FALSE, TRUE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FALSE);
    if (!r) r = consumeToken(b, TRUE);
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
  // identifier | identifier AS identifier
  static boolean create_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "create_name")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = parseTokens(b, 0, IDENTIFIER, AS, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // identifier COLON type
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
    Marker m = enter_section_(b, l, _NONE_, "<decl stmt>");
    r = item(b, l + 1);
    if (!r) r = let_decl(b, l + 1);
    exit_section_(b, l, m, DECL_STMT, r, false, null);
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
  // 'abc1'
  public static boolean enum_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enum_item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<enum item>");
    r = consumeToken(b, "abc1");
    exit_section_(b, l, m, ENUM_ITEM, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (expr (COMMA expr)*)?
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
  // LT type_expr (COMMA type_expr)* GT | expr_path
  static boolean expr_path_tail(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_path_tail")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr_path_tail_0(b, l + 1);
    if (!r) r = expr_path(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LT type_expr (COMMA type_expr)* GT
  private static boolean expr_path_tail_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_path_tail_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && type_expr(b, l + 1);
    r = r && expr_path_tail_0_2(b, l + 1);
    r = r && consumeToken(b, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA type_expr)*
  private static boolean expr_path_tail_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_path_tail_0_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!expr_path_tail_0_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "expr_path_tail_0_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA type_expr
  private static boolean expr_path_tail_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_path_tail_0_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && type_expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // expr SEMICOLON
  public static boolean expr_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_stmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<expr stmt>");
    r = expr(b, l + 1, -1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, l, m, EXPR_STMT, r, false, null);
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
  // EXTERN CRATE create_name
  public static boolean extern_crate_decl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extern_crate_decl")) return false;
    if (!nextTokenIs(b, EXTERN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, EXTERN, CRATE);
    r = r && create_name(b, l + 1);
    exit_section_(b, m, EXTERN_CRATE_DECL, r);
    return r;
  }

  /* ********************************************************** */
  // item *
  static boolean file(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "file")) return false;
    int c = current_position_(b);
    while (true) {
      if (!item(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "file", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  /* ********************************************************** */
  // FN identifier fn_sig block
  public static boolean fn_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_item")) return false;
    if (!nextTokenIs(b, FN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokens(b, 0, FN, IDENTIFIER);
    r = r && fn_sig(b, l + 1);
    p = r; // pin = 3
    r = r && block(b, l + 1);
    exit_section_(b, l, m, FN_ITEM, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // LPAREN (decl_item (COMMA decl_item)*)? RPAREN ARROW type
  static boolean fn_sig(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_sig")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeToken(b, LPAREN);
    r = r && fn_sig_1(b, l + 1);
    p = r; // pin = 2
    r = r && report_error_(b, consumeTokens(b, -1, RPAREN, ARROW));
    r = p && type(b, l + 1) && r;
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  // (decl_item (COMMA decl_item)*)?
  private static boolean fn_sig_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_sig_1")) return false;
    fn_sig_1_0(b, l + 1);
    return true;
  }

  // decl_item (COMMA decl_item)*
  private static boolean fn_sig_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_sig_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = decl_item(b, l + 1);
    r = r && fn_sig_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA decl_item)*
  private static boolean fn_sig_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_sig_1_0_1")) return false;
    int c = current_position_(b);
    while (true) {
      if (!fn_sig_1_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "fn_sig_1_0_1", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COMMA decl_item
  private static boolean fn_sig_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fn_sig_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && decl_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'abc3'
  public static boolean foreign_fn_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreign_fn_item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<foreign fn item>");
    r = consumeToken(b, "abc3");
    exit_section_(b, l, m, FOREIGN_FN_ITEM, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (identifier (COMMA identifier)*)?
  static boolean ident_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ident_list")) return false;
    ident_list_0(b, l + 1);
    return true;
  }

  // identifier (COMMA identifier)*
  private static boolean ident_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ident_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && ident_list_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA identifier)*
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

  // COMMA identifier
  private static boolean ident_list_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ident_list_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COMMA, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'abc6'
  public static boolean impl_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "impl_item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<impl item>");
    r = consumeToken(b, "abc6");
    exit_section_(b, l, m, IMPL_ITEM, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // vis? mod_item
  //        |      fn_item
  //        |      type_item
  //        |      struct_item
  //        |      enum_item
  //        |      const_item
  //        |      static_item
  //        |      trait_item
  //        |      impl_item
  //        |      extern_block
  public static boolean item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<item>");
    r = item_0(b, l + 1);
    if (!r) r = fn_item(b, l + 1);
    if (!r) r = type_item(b, l + 1);
    if (!r) r = struct_item(b, l + 1);
    if (!r) r = enum_item(b, l + 1);
    if (!r) r = const_item(b, l + 1);
    if (!r) r = static_item(b, l + 1);
    if (!r) r = trait_item(b, l + 1);
    if (!r) r = impl_item(b, l + 1);
    if (!r) r = extern_block(b, l + 1);
    exit_section_(b, l, m, ITEM, r, false, null);
    return r;
  }

  // vis? mod_item
  private static boolean item_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = item_0_0(b, l + 1);
    r = r && mod_item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // vis?
  private static boolean item_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item_0_0")) return false;
    vis(b, l + 1);
    return true;
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
  // 'abc8'
  static boolean lifetime(PsiBuilder b, int l) {
    return consumeToken(b, "abc8");
  }

  /* ********************************************************** */
  // ( STRING_LITERAL
  //                     | CHAR_LITERAL
  //                     | INTEGER_LITERAL
  //                     | FLOAT_LITERAL
  //                     | bool_literal ) literal_suffix?
  static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = literal_0(b, l + 1);
    r = r && literal_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // STRING_LITERAL
  //                     | CHAR_LITERAL
  //                     | INTEGER_LITERAL
  //                     | FLOAT_LITERAL
  //                     | bool_literal
  private static boolean literal_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STRING_LITERAL);
    if (!r) r = consumeToken(b, CHAR_LITERAL);
    if (!r) r = consumeToken(b, INTEGER_LITERAL);
    if (!r) r = consumeToken(b, FLOAT_LITERAL);
    if (!r) r = bool_literal(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // literal_suffix?
  private static boolean literal_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_1")) return false;
    literal_suffix(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // identifier
  static boolean literal_suffix(PsiBuilder b, int l) {
    return consumeToken(b, IDENTIFIER);
  }

  /* ********************************************************** */
  // attr* MatchPattern ARROW (expr COMMA | LBRACE block RBRACE)
  static boolean match_arm(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_arm")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = match_arm_0(b, l + 1);
    r = r && MatchPattern(b, l + 1);
    r = r && consumeToken(b, ARROW);
    r = r && match_arm_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // attr*
  private static boolean match_arm_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "match_arm_0")) return false;
    int c = current_position_(b);
    while (true) {
      if (!attr(b, l + 1)) break;
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
  // identifier (EQ literal | LPAREN meta_item_seq RPAREN)?
  static boolean meta_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && meta_item_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (EQ literal | LPAREN meta_item_seq RPAREN)?
  private static boolean meta_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_1")) return false;
    meta_item_1_0(b, l + 1);
    return true;
  }

  // EQ literal | LPAREN meta_item_seq RPAREN
  private static boolean meta_item_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = meta_item_1_0_0(b, l + 1);
    if (!r) r = meta_item_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // EQ literal
  private static boolean meta_item_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EQ);
    r = r && literal(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN meta_item_seq RPAREN
  private static boolean meta_item_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && meta_item_seq(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // meta_item [ COMMA meta_item_seq ]
  static boolean meta_item_seq(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_seq")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = meta_item(b, l + 1);
    r = r && meta_item_seq_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ COMMA meta_item_seq ]
  private static boolean meta_item_seq_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_seq_1")) return false;
    meta_item_seq_1_0(b, l + 1);
    return true;
  }

  // COMMA meta_item_seq
  private static boolean meta_item_seq_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "meta_item_seq_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && meta_item_seq(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (view_item | item)*
  static boolean mod_content(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_content")) return false;
    int c = current_position_(b);
    while (true) {
      if (!mod_content_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "mod_content", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // view_item | item
  private static boolean mod_content_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_content_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = view_item(b, l + 1);
    if (!r) r = item(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // MOD identifier (SEMICOLON | LBRACE mod_content RBRACE)
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

  // SEMICOLON | LBRACE mod_content RBRACE
  private static boolean mod_item_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_item_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMICOLON);
    if (!r) r = mod_item_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE mod_content RBRACE
  private static boolean mod_item_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mod_item_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && mod_content(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'abc7'
  static boolean no_struct_lit_expr(PsiBuilder b, int l) {
    return consumeToken(b, "abc7");
  }

  /* ********************************************************** */
  // LPAREN expr_list RPAREN
  static boolean par_expr_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "par_expr_list")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && expr_list(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'xyz1'
  static boolean pat(PsiBuilder b, int l) {
    return consumeToken(b, "xyz1");
  }

  /* ********************************************************** */
  // identifier | SELF
  public static boolean path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path")) return false;
    if (!nextTokenIs(b, "<path>", SELF, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<path>");
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, SELF);
    exit_section_(b, l, m, PATH, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // identifier (COLONCOLON (path_glob | MUL))?
  //             | LBRACE path (COMMA path)* RBRACE
  public static boolean path_glob(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_glob")) return false;
    if (!nextTokenIs(b, "<path glob>", LBRACE, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<path glob>");
    r = path_glob_0(b, l + 1);
    if (!r) r = path_glob_1(b, l + 1);
    exit_section_(b, l, m, PATH_GLOB, r, false, null);
    return r;
  }

  // identifier (COLONCOLON (path_glob | MUL))?
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
  // STATIC decl_item EQ expr SEMICOLON
  public static boolean static_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "static_item")) return false;
    if (!nextTokenIs(b, STATIC)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STATIC);
    r = r && decl_item(b, l + 1);
    r = r && consumeToken(b, EQ);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, STATIC_ITEM, r);
    return r;
  }

  /* ********************************************************** */
  // expr_stmt | decl_stmt
  public static boolean stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, "<stmt>");
    r = expr_stmt(b, l + 1);
    if (!r) r = decl_stmt(b, l + 1);
    exit_section_(b, l, m, STMT, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'abc2'
  public static boolean struct_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<struct item>");
    r = consumeToken(b, "abc2");
    exit_section_(b, l, m, STRUCT_ITEM, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'abc4'
  public static boolean trait_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trait_item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<trait item>");
    r = consumeToken(b, "abc4");
    exit_section_(b, l, m, TRAIT_ITEM, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // identifier
  static boolean type(PsiBuilder b, int l) {
    return consumeToken(b, IDENTIFIER);
  }

  /* ********************************************************** */
  // 'abc'
  static boolean type_expr(PsiBuilder b, int l) {
    return consumeToken(b, "abc");
  }

  /* ********************************************************** */
  // 'abc0'
  public static boolean type_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type_item")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<type item>");
    r = consumeToken(b, "abc0");
    exit_section_(b, l, m, TYPE_ITEM, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // vis? USE (path AS identifier | path_glob)
  public static boolean use_decl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "use_decl")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<use decl>");
    r = use_decl_0(b, l + 1);
    r = r && consumeToken(b, USE);
    r = r && use_decl_2(b, l + 1);
    exit_section_(b, l, m, USE_DECL, r, false, null);
    return r;
  }

  // vis?
  private static boolean use_decl_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "use_decl_0")) return false;
    vis(b, l + 1);
    return true;
  }

  // path AS identifier | path_glob
  private static boolean use_decl_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "use_decl_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = use_decl_2_0(b, l + 1);
    if (!r) r = path_glob(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // path AS identifier
  private static boolean use_decl_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "use_decl_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = path(b, l + 1);
    r = r && consumeTokens(b, 0, AS, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // extern_crate_decl | use_decl SEMICOLON
  static boolean view_item(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_item")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = extern_crate_decl(b, l + 1);
    if (!r) r = view_item_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // use_decl SEMICOLON
  private static boolean view_item_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "view_item_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = use_decl(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // PUB | PRIV
  static boolean vis(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "vis")) return false;
    if (!nextTokenIs(b, "", PRIV, PUB)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PUB);
    if (!r) r = consumeToken(b, PRIV);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // Expression root: expr
  // Operator priority table:
  // 0: BINARY(eq_bin_expr) BINARY(eq_bit_or_bin_expr) BINARY(eq_bit_xor_bin_expr) BINARY(eq_bit_and_bin_expr) BINARY(eq_bit_gtgt_bin_expr) BINARY(eq_bit_ltlt_bin_expr) BINARY(eq_plus_bin_expr) BINARY(eq_minus_bin_expr) BINARY(eq_mul_bin_expr) BINARY(eq_div_bin_expr) BINARY(eq_rem_bin_expr)
  // 1: BINARY(bool_or_bin_expr)
  // 2: BINARY(bool_and_bin_expr)
  // 3: BINARY(bit_or_bin_expr)
  // 4: BINARY(bit_xor_bin_expr)
  // 5: BINARY(bit_and_bin_expr)
  // 6: BINARY(eq_comp_bin_expr) BINARY(ineq_comp_bin_expr)
  // 7: BINARY(lt_comp_bin_expr) BINARY(gt_comp_bin_expr) BINARY(lteq_comp_bin_expr) BINARY(gteq_comp_bin_expr)
  // 8: BINARY(bit_left_shift_bin_expr) BINARY(bit_right_shift_bin_expr)
  // 9: BINARY(plus_bin_expr) BINARY(minus_bin_expr)
  // 10: BINARY(mul_bin_expr) BINARY(div_bin_expr) BINARY(rem_bin_expr)
  // 11: PREFIX(unary_expr)
  // 12: ATOM(block_expr)
  // 13: PREFIX(lambda_expr)
  // 14: ATOM(struct_expr)
  // 15: ATOM(while_expr)
  // 16: ATOM(loop_expr)
  // 17: ATOM(cont_expr)
  // 18: ATOM(break_expr)
  // 19: ATOM(for_expr)
  // 20: ATOM(if_expr)
  // 21: ATOM(match_expr)
  // 22: ATOM(if_let_expr)
  // 23: PREFIX(while_let_expr)
  // 24: ATOM(ret_expr)
  // 25: ATOM(expr_path)
  // 26: PREFIX(paren_expr)
  // 27: ATOM(tuple_expr)
  // 28: ATOM(unit_expr)
  // 29: ATOM(array_expr)
  // 30: BINARY(full_range_expr) POSTFIX(right_open_expr) PREFIX(left_open_expr) ATOM(full_open_expr)
  // 31: BINARY(index_expr)
  // 32: POSTFIX(call_expr)
  // 33: POSTFIX(method_call_expr)
  // 34: POSTFIX(field_expr)
  // 35: ATOM(literal_expr)
  public static boolean expr(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr")) return false;
    addVariant(b, "<expr>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expr>");
    r = unary_expr(b, l + 1);
    if (!r) r = block_expr(b, l + 1);
    if (!r) r = lambda_expr(b, l + 1);
    if (!r) r = struct_expr(b, l + 1);
    if (!r) r = while_expr(b, l + 1);
    if (!r) r = loop_expr(b, l + 1);
    if (!r) r = cont_expr(b, l + 1);
    if (!r) r = break_expr(b, l + 1);
    if (!r) r = for_expr(b, l + 1);
    if (!r) r = if_expr(b, l + 1);
    if (!r) r = match_expr(b, l + 1);
    if (!r) r = if_let_expr(b, l + 1);
    if (!r) r = while_let_expr(b, l + 1);
    if (!r) r = ret_expr(b, l + 1);
    if (!r) r = expr_path(b, l + 1);
    if (!r) r = paren_expr(b, l + 1);
    if (!r) r = tuple_expr(b, l + 1);
    if (!r) r = unit_expr(b, l + 1);
    if (!r) r = array_expr(b, l + 1);
    if (!r) r = left_open_expr(b, l + 1);
    if (!r) r = literal_expr(b, l + 1);
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
      if (g < 0 && consumeTokenSmart(b, EQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, OREQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_BIT_OR_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, XOREQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_BIT_XOR_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, ANDEQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_BIT_AND_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, GTGTEQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_BIT_GTGT_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, LTLTEQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_BIT_LTLT_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, PLUSEQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_PLUS_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, MINUSEQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_MINUS_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, MULEQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_MUL_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, DIVEQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_DIV_BIN_EXPR, r, true, null);
      }
      else if (g < 0 && consumeTokenSmart(b, REMEQ)) {
        r = expr(b, l, -1);
        exit_section_(b, l, m, EQ_REM_BIN_EXPR, r, true, null);
      }
      else if (g < 1 && consumeTokenSmart(b, OROR)) {
        r = expr(b, l, 1);
        exit_section_(b, l, m, BOOL_OR_BIN_EXPR, r, true, null);
      }
      else if (g < 2 && consumeTokenSmart(b, ANDAND)) {
        r = expr(b, l, 2);
        exit_section_(b, l, m, BOOL_AND_BIN_EXPR, r, true, null);
      }
      else if (g < 3 && consumeTokenSmart(b, OR)) {
        r = expr(b, l, 3);
        exit_section_(b, l, m, BIT_OR_BIN_EXPR, r, true, null);
      }
      else if (g < 4 && consumeTokenSmart(b, XOR)) {
        r = expr(b, l, 4);
        exit_section_(b, l, m, BIT_XOR_BIN_EXPR, r, true, null);
      }
      else if (g < 5 && consumeTokenSmart(b, AND)) {
        r = expr(b, l, 5);
        exit_section_(b, l, m, BIT_AND_BIN_EXPR, r, true, null);
      }
      else if (g < 6 && consumeTokenSmart(b, EXCLEQ)) {
        r = expr(b, l, 6);
        exit_section_(b, l, m, INEQ_COMP_BIN_EXPR, r, true, null);
      }
      else if (g < 7 && consumeTokenSmart(b, LT)) {
        r = expr(b, l, 7);
        exit_section_(b, l, m, LT_COMP_BIN_EXPR, r, true, null);
      }
      else if (g < 7 && consumeTokenSmart(b, GT)) {
        r = expr(b, l, 7);
        exit_section_(b, l, m, GT_COMP_BIN_EXPR, r, true, null);
      }
      else if (g < 7 && consumeTokenSmart(b, LTEQ)) {
        r = expr(b, l, 7);
        exit_section_(b, l, m, LTEQ_COMP_BIN_EXPR, r, true, null);
      }
      else if (g < 7 && consumeTokenSmart(b, GTEQ)) {
        r = expr(b, l, 7);
        exit_section_(b, l, m, GTEQ_COMP_BIN_EXPR, r, true, null);
      }
      else if (g < 8 && consumeTokenSmart(b, LTLT)) {
        r = expr(b, l, 8);
        exit_section_(b, l, m, BIT_LEFT_SHIFT_BIN_EXPR, r, true, null);
      }
      else if (g < 8 && consumeTokenSmart(b, GTGT)) {
        r = expr(b, l, 8);
        exit_section_(b, l, m, BIT_RIGHT_SHIFT_BIN_EXPR, r, true, null);
      }
      else if (g < 9 && consumeTokenSmart(b, PLUS)) {
        r = expr(b, l, 9);
        exit_section_(b, l, m, PLUS_BIN_EXPR, r, true, null);
      }
      else if (g < 9 && consumeTokenSmart(b, MINUS)) {
        r = expr(b, l, 9);
        exit_section_(b, l, m, MINUS_BIN_EXPR, r, true, null);
      }
      else if (g < 10 && consumeTokenSmart(b, MUL)) {
        r = expr(b, l, 10);
        exit_section_(b, l, m, MUL_BIN_EXPR, r, true, null);
      }
      else if (g < 10 && consumeTokenSmart(b, DIV)) {
        r = expr(b, l, 10);
        exit_section_(b, l, m, DIV_BIN_EXPR, r, true, null);
      }
      else if (g < 10 && consumeTokenSmart(b, REM)) {
        r = expr(b, l, 10);
        exit_section_(b, l, m, REM_BIN_EXPR, r, true, null);
      }
      else if (g < 30 && consumeTokenSmart(b, DOTDOT)) {
        r = expr(b, l, 30);
        exit_section_(b, l, m, FULL_RANGE_EXPR, r, true, null);
      }
      else if (g < 31 && consumeTokenSmart(b, LBRACK)) {
        r = report_error_(b, expr(b, l, 31));
        r = consumeToken(b, RBRACK) && r;
        exit_section_(b, l, m, INDEX_EXPR, r, true, null);
      }
      else if (g < 32 && par_expr_list(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, CALL_EXPR, r, true, null);
      }
      else if (g < 33 && method_call_expr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, METHOD_CALL_EXPR, r, true, null);
      }
      else if (g < 34 && parseTokensSmart(b, 0, DOT, IDENTIFIER)) {
        r = true;
        exit_section_(b, l, m, FIELD_EXPR, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  public static boolean unary_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = unary_expr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, 11);
    exit_section_(b, l, m, UNARY_EXPR, r, p, null);
    return r || p;
  }

  // MINUS | MUL | EXCL
  private static boolean unary_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, MINUS);
    if (!r) r = consumeTokenSmart(b, MUL);
    if (!r) r = consumeTokenSmart(b, EXCL);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE (stmt SEMICOLON | item)* (expr) RBRACE
  public static boolean block_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_expr")) return false;
    if (!nextTokenIsFast(b, LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, LBRACE);
    p = r; // pin = .*
    r = r && report_error_(b, block_expr_1(b, l + 1));
    r = p && report_error_(b, block_expr_2(b, l + 1)) && r;
    r = p && consumeToken(b, RBRACE) && r;
    exit_section_(b, l, m, BLOCK_EXPR, r, p, null);
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
    boolean r;
    Marker m = enter_section_(b);
    r = stmt(b, l + 1);
    r = r && consumeToken(b, SEMICOLON);
    exit_section_(b, m, null, r);
    return r;
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

  // expr_path LBRACE  identifier COLON expr
  //                          (COMMA   identifier COLON expr)*
  //                          (DOTDOT  expr)? RBRACE
  //               | expr_path LPAREN expr
  //                          (COMMA  expr)* RPAREN
  //               | expr_path
  public static boolean struct_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr")) return false;
    if (!nextTokenIsFast(b, COLONCOLON, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, "<struct expr>");
    r = struct_expr_0(b, l + 1);
    if (!r) r = struct_expr_1(b, l + 1);
    if (!r) r = expr_path(b, l + 1);
    exit_section_(b, l, m, STRUCT_EXPR, r, false, null);
    return r;
  }

  // expr_path LBRACE  identifier COLON expr
  //                          (COMMA   identifier COLON expr)*
  //                          (DOTDOT  expr)? RBRACE
  private static boolean struct_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr_path(b, l + 1);
    r = r && consumeTokens(b, 0, LBRACE, IDENTIFIER, COLON);
    r = r && expr(b, l + 1, -1);
    r = r && struct_expr_0_5(b, l + 1);
    r = r && struct_expr_0_6(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA   identifier COLON expr)*
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

  // COMMA   identifier COLON expr
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

  // expr_path LPAREN expr
  //                          (COMMA  expr)* RPAREN
  private static boolean struct_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "struct_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr_path(b, l + 1);
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

  // lifetime COLON WHILE no_struct_lit_expr LBRACE block RBRACE
  public static boolean while_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<while expr>");
    r = lifetime(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, WHILE);
    r = r && no_struct_lit_expr(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, WHILE_EXPR, r, false, null);
    return r;
  }

  // lifetime COLON LOOP LBRACE block RBRACE
  public static boolean loop_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "loop_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<loop expr>");
    r = lifetime(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, LOOP, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, LOOP_EXPR, r, false, null);
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
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<for expr>");
    r = lifetime(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, FOR);
    r = r && pat(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && no_struct_lit_expr(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && block(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, FOR_EXPR, r, false, null);
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
    r = p && expr(b, l, 23);
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

  // COLONCOLON? identifier (COLONCOLON expr_path_tail)*
  public static boolean expr_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_path")) return false;
    if (!nextTokenIsFast(b, COLONCOLON, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<expr path>");
    r = expr_path_0(b, l + 1);
    r = r && consumeToken(b, IDENTIFIER);
    r = r && expr_path_2(b, l + 1);
    exit_section_(b, l, m, EXPR_PATH, r, false, null);
    return r;
  }

  // COLONCOLON?
  private static boolean expr_path_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_path_0")) return false;
    consumeTokenSmart(b, COLONCOLON);
    return true;
  }

  // (COLONCOLON expr_path_tail)*
  private static boolean expr_path_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_path_2")) return false;
    int c = current_position_(b);
    while (true) {
      if (!expr_path_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "expr_path_2", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // COLONCOLON expr_path_tail
  private static boolean expr_path_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_path_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, COLONCOLON);
    r = r && expr_path_tail(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean paren_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "paren_expr")) return false;
    if (!nextTokenIsFast(b, LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, LPAREN);
    p = r;
    r = p && expr(b, l, 26);
    r = p && report_error_(b, consumeToken(b, RPAREN)) && r;
    exit_section_(b, l, m, PAREN_EXPR, r, p, null);
    return r || p;
  }

  // LPAREN expr ((COMMA expr)* | COMMA) RPAREN
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

  // (COMMA expr)* | COMMA
  private static boolean tuple_expr_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_expr_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tuple_expr_2_0(b, l + 1);
    if (!r) r = consumeTokenSmart(b, COMMA);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA expr)*
  private static boolean tuple_expr_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tuple_expr_2_0")) return false;
    int c = current_position_(b);
    while (true) {
      if (!tuple_expr_2_0_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "tuple_expr_2_0", c)) break;
      c = current_position_(b);
    }
    return true;
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

  // LBRACK MUT? array_elems? RBRACK
  public static boolean array_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_expr")) return false;
    if (!nextTokenIsFast(b, LBRACK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LBRACK);
    r = r && array_expr_1(b, l + 1);
    r = r && array_expr_2(b, l + 1);
    r = r && consumeToken(b, RBRACK);
    exit_section_(b, m, ARRAY_EXPR, r);
    return r;
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

  public static boolean left_open_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "left_open_expr")) return false;
    if (!nextTokenIsFast(b, DOTDOT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, DOTDOT);
    p = r;
    r = p && expr(b, l, 30);
    exit_section_(b, l, m, LEFT_OPEN_EXPR, r, p, null);
    return r || p;
  }

  // DOTDOT
  public static boolean full_open_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "full_open_expr")) return false;
    if (!nextTokenIsFast(b, DOTDOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, DOTDOT);
    exit_section_(b, m, FULL_OPEN_EXPR, r);
    return r;
  }

  // DOT identifier par_expr_list
  private static boolean method_call_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "method_call_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOT, IDENTIFIER);
    r = r && par_expr_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // literal
  public static boolean literal_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, "<literal expr>");
    r = literal(b, l + 1);
    exit_section_(b, l, m, LITERAL_EXPR, r, false, null);
    return r;
  }

}
