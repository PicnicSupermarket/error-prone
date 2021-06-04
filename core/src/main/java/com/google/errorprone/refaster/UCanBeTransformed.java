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
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.Context;

import javax.annotation.Nullable;

import static com.google.errorprone.refaster.Unifier.unifications;

@AutoValue
abstract class UCanBeTransformed extends UExpression {
  public static UCanBeTransformed create(UExpression expression, Type beforeTemplateType, Type afterTemplateType) {
    return new AutoValue_UCanBeTransformed(expression, beforeTemplateType, afterTemplateType);
  }

  abstract UExpression expression();

  abstract Type beforeTemplateType();

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
  @Nullable
  protected Choice<Unifier> defaultAction(Tree tree, @Nullable Unifier unifier) {
    final Type afterTemplateType = afterTemplateType();
    final Type beforeTemplateType = beforeTemplateType();

//    final Tree exprTarget = ASTHelpers.stripParentheses(tree);
//    return expression().unify(exprTarget,unifier)
//            .condition(true);
//    return expression().unify(tree, unifier);
    return expression().unify(tree, unifier).thenChoose((Unifier u)  -> {
      return Choice.none();
    });
//    final Tree exprTarget = ASTHelpers.stripParentheses(target);
//    return expression()
//            .unify(exprTarget, unifier)
//            .condition((Unifier success) -> matches(exprTarget, success) == positive());
  }

}
