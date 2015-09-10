// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import org.rust.lang.core.resolve.scope.RustResolveScope;

public class RustVisitor extends PsiElementVisitor {

  public void visitAbi(@NotNull RustAbi o) {
    visitCompositeElement(o);
  }

  public void visitAnonParam(@NotNull RustAnonParam o) {
    visitCompositeElement(o);
  }

  public void visitAnonParams(@NotNull RustAnonParams o) {
    visitCompositeElement(o);
  }

  public void visitArgList(@NotNull RustArgList o) {
    visitCompositeElement(o);
  }

  public void visitArrayExpr(@NotNull RustArrayExpr o) {
    visitExpr(o);
  }

  public void visitBinaryExpr(@NotNull RustBinaryExpr o) {
    visitExpr(o);
  }

  public void visitBinding(@NotNull RustBinding o) {
    visitCompositeElement(o);
  }

  public void visitBindingMode(@NotNull RustBindingMode o) {
    visitCompositeElement(o);
  }

  public void visitBindings(@NotNull RustBindings o) {
    visitCompositeElement(o);
  }

  public void visitBitAndBinExpr(@NotNull RustBitAndBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitBitLeftShiftBinExpr(@NotNull RustBitLeftShiftBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitBitOrBinExpr(@NotNull RustBitOrBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitBitRightShiftBinExpr(@NotNull RustBitRightShiftBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitBitXorBinExpr(@NotNull RustBitXorBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitBlock(@NotNull RustBlock o) {
    visitCompositeElement(o);
  }

  public void visitBlockExpr(@NotNull RustBlockExpr o) {
    visitExpr(o);
  }

  public void visitBoolAndBinExpr(@NotNull RustBoolAndBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitBoolOrBinExpr(@NotNull RustBoolOrBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitBound(@NotNull RustBound o) {
    visitCompositeElement(o);
  }

  public void visitBounds(@NotNull RustBounds o) {
    visitCompositeElement(o);
  }

  public void visitBreakExpr(@NotNull RustBreakExpr o) {
    visitExpr(o);
  }

  public void visitCallExpr(@NotNull RustCallExpr o) {
    visitExpr(o);
  }

  public void visitConstItem(@NotNull RustConstItem o) {
    visitCompositeElement(o);
  }

  public void visitContExpr(@NotNull RustContExpr o) {
    visitExpr(o);
  }

  public void visitDeclItem(@NotNull RustDeclItem o) {
    visitCompositeElement(o);
  }

  public void visitDeclStmt(@NotNull RustDeclStmt o) {
    visitStmt(o);
  }

  public void visitDivBinExpr(@NotNull RustDivBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEnumArgs(@NotNull RustEnumArgs o) {
    visitCompositeElement(o);
  }

  public void visitEnumDef(@NotNull RustEnumDef o) {
    visitCompositeElement(o);
  }

  public void visitEnumItem(@NotNull RustEnumItem o) {
    visitCompositeElement(o);
  }

  public void visitEqBinExpr(@NotNull RustEqBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqBitAndBinExpr(@NotNull RustEqBitAndBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqBitGtgtBinExpr(@NotNull RustEqBitGtgtBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqBitLtltBinExpr(@NotNull RustEqBitLtltBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqBitOrBinExpr(@NotNull RustEqBitOrBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqBitXorBinExpr(@NotNull RustEqBitXorBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqCompBinExpr(@NotNull RustEqCompBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqDivBinExpr(@NotNull RustEqDivBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqMinusBinExpr(@NotNull RustEqMinusBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqMulBinExpr(@NotNull RustEqMulBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqPlusBinExpr(@NotNull RustEqPlusBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitEqRemBinExpr(@NotNull RustEqRemBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitExpr(@NotNull RustExpr o) {
    visitCompositeElement(o);
  }

  public void visitExprPath(@NotNull RustExprPath o) {
    visitExpr(o);
  }

  public void visitExprStmt(@NotNull RustExprStmt o) {
    visitStmt(o);
  }

  public void visitExternBlock(@NotNull RustExternBlock o) {
    visitCompositeElement(o);
  }

  public void visitExternCrateDecl(@NotNull RustExternCrateDecl o) {
    visitCompositeElement(o);
  }

  public void visitExternFnItem(@NotNull RustExternFnItem o) {
    visitCompositeElement(o);
  }

  public void visitFieldExpr(@NotNull RustFieldExpr o) {
    visitExpr(o);
  }

  public void visitFnItem(@NotNull RustFnItem o) {
    visitResolveScope(o);
  }

  public void visitFnParams(@NotNull RustFnParams o) {
    visitCompositeElement(o);
  }

  public void visitForExpr(@NotNull RustForExpr o) {
    visitExpr(o);
  }

  public void visitForLifetimes(@NotNull RustForLifetimes o) {
    visitCompositeElement(o);
  }

  public void visitForeignFnItem(@NotNull RustForeignFnItem o) {
    visitCompositeElement(o);
  }

  public void visitForeignItem(@NotNull RustForeignItem o) {
    visitCompositeElement(o);
  }

  public void visitForeignModItem(@NotNull RustForeignModItem o) {
    visitCompositeElement(o);
  }

  public void visitFullRangeExpr(@NotNull RustFullRangeExpr o) {
    visitRangeExpr(o);
  }

  public void visitGenericParams(@NotNull RustGenericParams o) {
    visitCompositeElement(o);
  }

  public void visitGtCompBinExpr(@NotNull RustGtCompBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitGteqCompBinExpr(@NotNull RustGteqCompBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitIfExpr(@NotNull RustIfExpr o) {
    visitExpr(o);
  }

  public void visitIfLetExpr(@NotNull RustIfLetExpr o) {
    visitExpr(o);
  }

  public void visitImplItem(@NotNull RustImplItem o) {
    visitCompositeElement(o);
  }

  public void visitImplMethod(@NotNull RustImplMethod o) {
    visitCompositeElement(o);
  }

  public void visitIndexExpr(@NotNull RustIndexExpr o) {
    visitExpr(o);
  }

  public void visitIneqCompBinExpr(@NotNull RustIneqCompBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitInnerAttr(@NotNull RustInnerAttr o) {
    visitCompositeElement(o);
  }

  public void visitItem(@NotNull RustItem o) {
    visitCompositeElement(o);
  }

  public void visitLambdaExpr(@NotNull RustLambdaExpr o) {
    visitExpr(o);
  }

  public void visitLetDecl(@NotNull RustLetDecl o) {
    visitCompositeElement(o);
  }

  public void visitLifetimes(@NotNull RustLifetimes o) {
    visitCompositeElement(o);
  }

  public void visitLitExpr(@NotNull RustLitExpr o) {
    visitExpr(o);
  }

  public void visitLoopExpr(@NotNull RustLoopExpr o) {
    visitExpr(o);
  }

  public void visitLtCompBinExpr(@NotNull RustLtCompBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitLteqCompBinExpr(@NotNull RustLteqCompBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitMatchExpr(@NotNull RustMatchExpr o) {
    visitExpr(o);
  }

  public void visitMetaItem(@NotNull RustMetaItem o) {
    visitCompositeElement(o);
  }

  public void visitMethod(@NotNull RustMethod o) {
    visitCompositeElement(o);
  }

  public void visitMethodCallExpr(@NotNull RustMethodCallExpr o) {
    visitExpr(o);
  }

  public void visitMinusBinExpr(@NotNull RustMinusBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitModItem(@NotNull RustModItem o) {
    visitCompositeElement(o);
  }

  public void visitMulBinExpr(@NotNull RustMulBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitOpenRangeExpr(@NotNull RustOpenRangeExpr o) {
    visitRangeExpr(o);
  }

  public void visitOuterAttr(@NotNull RustOuterAttr o) {
    visitCompositeElement(o);
  }

  public void visitParam(@NotNull RustParam o) {
    visitCompositeElement(o);
  }

  public void visitParenExpr(@NotNull RustParenExpr o) {
    visitExpr(o);
  }

  public void visitPat(@NotNull RustPat o) {
    visitCompositeElement(o);
  }

  public void visitPatStruct(@NotNull RustPatStruct o) {
    visitCompositeElement(o);
  }

  public void visitPatTup(@NotNull RustPatTup o) {
    visitCompositeElement(o);
  }

  public void visitPatVec(@NotNull RustPatVec o) {
    visitCompositeElement(o);
  }

  public void visitPath(@NotNull RustPath o) {
    visitCompositeElement(o);
  }

  public void visitPathExpr(@NotNull RustPathExpr o) {
    visitExpr(o);
  }

  public void visitPathGlob(@NotNull RustPathGlob o) {
    visitCompositeElement(o);
  }

  public void visitPlusBinExpr(@NotNull RustPlusBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitPolybound(@NotNull RustPolybound o) {
    visitCompositeElement(o);
  }

  public void visitRangeExpr(@NotNull RustRangeExpr o) {
    visitExpr(o);
  }

  public void visitRemBinExpr(@NotNull RustRemBinExpr o) {
    visitBinaryExpr(o);
  }

  public void visitRetExpr(@NotNull RustRetExpr o) {
    visitExpr(o);
  }

  public void visitRetType(@NotNull RustRetType o) {
    visitCompositeElement(o);
  }

  public void visitStaticItem(@NotNull RustStaticItem o) {
    visitCompositeElement(o);
  }

  public void visitStmt(@NotNull RustStmt o) {
    visitCompositeElement(o);
  }

  public void visitStmtItem(@NotNull RustStmtItem o) {
    visitCompositeElement(o);
  }

  public void visitStructDeclArgs(@NotNull RustStructDeclArgs o) {
    visitCompositeElement(o);
  }

  public void visitStructDeclField(@NotNull RustStructDeclField o) {
    visitCompositeElement(o);
  }

  public void visitStructExpr(@NotNull RustStructExpr o) {
    visitExpr(o);
  }

  public void visitStructItem(@NotNull RustStructItem o) {
    visitCompositeElement(o);
  }

  public void visitStructTupleArgs(@NotNull RustStructTupleArgs o) {
    visitCompositeElement(o);
  }

  public void visitStructTupleField(@NotNull RustStructTupleField o) {
    visitCompositeElement(o);
  }

  public void visitTraitConst(@NotNull RustTraitConst o) {
    visitCompositeElement(o);
  }

  public void visitTraitItem(@NotNull RustTraitItem o) {
    visitCompositeElement(o);
  }

  public void visitTraitMethod(@NotNull RustTraitMethod o) {
    visitCompositeElement(o);
  }

  public void visitTraitRef(@NotNull RustTraitRef o) {
    visitCompositeElement(o);
  }

  public void visitTraitType(@NotNull RustTraitType o) {
    visitCompositeElement(o);
  }

  public void visitTupleExpr(@NotNull RustTupleExpr o) {
    visitExpr(o);
  }

  public void visitTypeAscription(@NotNull RustTypeAscription o) {
    visitCompositeElement(o);
  }

  public void visitTypeItem(@NotNull RustTypeItem o) {
    visitCompositeElement(o);
  }

  public void visitTypeMethod(@NotNull RustTypeMethod o) {
    visitCompositeElement(o);
  }

  public void visitTypeParam(@NotNull RustTypeParam o) {
    visitCompositeElement(o);
  }

  public void visitTypeParamBounds(@NotNull RustTypeParamBounds o) {
    visitCompositeElement(o);
  }

  public void visitTypeParamDefault(@NotNull RustTypeParamDefault o) {
    visitCompositeElement(o);
  }

  public void visitTypePrimSum(@NotNull RustTypePrimSum o) {
    visitCompositeElement(o);
  }

  public void visitUnaryExpr(@NotNull RustUnaryExpr o) {
    visitExpr(o);
  }

  public void visitUnitExpr(@NotNull RustUnitExpr o) {
    visitExpr(o);
  }

  public void visitUseItem(@NotNull RustUseItem o) {
    visitCompositeElement(o);
  }

  public void visitViewPath(@NotNull RustViewPath o) {
    visitCompositeElement(o);
  }

  public void visitViewPathPart(@NotNull RustViewPathPart o) {
    visitCompositeElement(o);
  }

  public void visitWhereClause(@NotNull RustWhereClause o) {
    visitCompositeElement(o);
  }

  public void visitWherePred(@NotNull RustWherePred o) {
    visitCompositeElement(o);
  }

  public void visitWhileExpr(@NotNull RustWhileExpr o) {
    visitExpr(o);
  }

  public void visitWhileLetExpr(@NotNull RustWhileLetExpr o) {
    visitExpr(o);
  }

  public void visitResolveScope(@NotNull RustResolveScope o) {
    visitCompositeElement(o);
  }

  public void visitCompositeElement(@NotNull RustCompositeElement o) {
    visitElement(o);
  }

}
