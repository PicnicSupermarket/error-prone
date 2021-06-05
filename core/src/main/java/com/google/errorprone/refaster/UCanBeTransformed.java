/*
 * Copyright 2013 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster;

import com.google.auto.value.AutoValue;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.Context;
import javax.annotation.Nullable;

@AutoValue
abstract class UCanBeTransformed extends UExpression {
  public static UCanBeTransformed create(UExpression expression, Type afterTemplateType) {
    return new AutoValue_UCanBeTransformed(expression, afterTemplateType);
  }

  abstract UExpression expression();

  // XXX: Review whether we can infer this from `expression()`.
  // XXX: Nope, this should go. Need to infer after unification.
  //  abstract Type beforeTemplateType();

  abstract Type afterTemplateType();

  @Override
  public JCExpression inline(Inliner inliner) throws CouldNotResolveImportException {
    return expression().inline(inliner);
  }

  @Override
  public <R, D> R accept(TreeVisitor<R, D> visitor, D data) {
    return expression().accept(visitor, data);
  }

  @Override
  public Choice<Unifier> unify(@Nullable Tree target, Unifier unifier) {
    return super.unify(target, unifier);
  }

  @Override
  public Kind getKind() {
    return expression().getKind();
  }

  @Override
  protected Choice<Unifier> defaultAction(Tree tree, Unifier unifier) {
    final Tree exprTarget = ASTHelpers.stripParentheses(tree);
    final Type afterTemplateType = afterTemplateType();
    UExpression expression = expression();
    final VisitorState state = makeVisitorState(tree, unifier);
    //    // XXX: Use.
    ASTHelpers.isSubtype(afterTemplateType, afterTemplateType, state);

    String bindingName = ((UFreeIdent) expression()).getName().contents();
    //    final Tree exprTarget = ASTHelpers.stripParentheses(tree);
    //    return expression().unify(exprTarget,unifier)
    //            .condition(true);
    //    return expression().unify(tree, unifier);
    return expression()
        .unify(tree, unifier)
        .condition(
            (Unifier success) -> {
              Type type = success.getBinding(new UFreeIdent.Key(bindingName)).type;
              Type other = afterTemplateType;
              boolean b = ASTHelpers.isSubtype(type, other, state);
              boolean b2 = ASTHelpers.isSubtype(other, type, state);
              boolean convertible = success.types().isConvertible(type, other);
              boolean convertible1 = success.types().isConvertible(other, type);
              boolean equals =
                  other.tsym.toString().equals(success.types().supertype(type).tsym.toString());
              boolean subtype =
                  ASTHelpers.isSubtype(
                      state.getTypeFromString(type.tsym.toString()),
                      state.getTypeFromString(other.tsym.toString()),
                      state);

              return subtype;
            });
  }

  // XXX: Move!
  static VisitorState makeVisitorState(Tree target, Unifier unifier) {
    Context context = unifier.getContext();
    TreePath path = TreePath.getPath(context.get(JCCompilationUnit.class), target);
    return new VisitorState(context).withPath(path);
  }
}
