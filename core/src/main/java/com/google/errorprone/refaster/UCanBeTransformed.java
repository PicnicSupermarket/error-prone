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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.Context;
import javax.annotation.Nullable;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.errorprone.util.ASTHelpers.getType;

@AutoValue
abstract class UCanBeTransformed extends UExpression {
  public static UCanBeTransformed create(UExpression expression, CType afterTemplateType) {
    return new AutoValue_UCanBeTransformed(expression, afterTemplateType);
  }

  abstract UExpression expression();

  abstract CType afterTemplateType();

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
    final VisitorState state = makeVisitorState(tree, unifier);
    String bindingName = ((UFreeIdent) expression()).getName().contents();

    return expression()
        .unify(tree, unifier)
        .condition(
            (Unifier success) -> {
//              if (tree instanceof LambdaExpressionTree) {
//                Type afterTemplateType = state.getTypeFromString("java.util.function.Function");
//                boolean canBeTransformed =
//                    canLambdaBeTransformed(tree, state, afterTemplateType, success);
//              }

              Type type = success.getBinding(new UFreeIdent.Key(bindingName)).type;
              boolean present = afterTemplateType()
                      .unify(tree, unifier).first().isPresent();

              return present;
            });
  }

  private boolean canLambdaBeTransformed(
      Tree tree, VisitorState state, Type afterTemplateType, Unifier success) {

    ImmutableList<Type> params = getParameterTypesOfLambda((LambdaExpressionTree) tree);
    List<Type> parameterTypesTarget =
        success.types().findDescriptorType(afterTemplateType).getParameterTypes();
    List<Type> bounds = success.types().getBounds((Type.TypeVar) parameterTypesTarget.get(0));
    boolean paramsMatch = hasMatchingParameters(params, ImmutableList.of(bounds.get(0)), state);

    //    ImmutableSet<Type> thrownExceptions =
    //        ASTHelpers.getThrownExceptions(((LambdaExpressionTree) tree).getBody(), state);
    //    List<Type> thrownTypesTargetExpression = afterTemplateType().baseType().getThrownTypes();
    //
    //
    //    Type returnTypeLambda =
    // state.getTypes().findDescriptorType(ASTHelpers.getType(tree)).getReturnType();
    //    Type returnTypeTarget =
    //            state.getTypes().findDescriptorType(afterTemplateType).getReturnType();
    //    boolean returnTypeMatches = ASTHelpers.isSubtype(returnTypeLambda, returnTypeTarget,
    // state);
    //
    //    return paramsMatch && returnTypeMatches;
    return false;
  }

  private ImmutableList<Type> getParameterTypesOfLambda(LambdaExpressionTree tree) {
    return tree.getParameters().stream()
        .map(param -> ASTHelpers.getSymbol(param).type)
        .collect(toImmutableList());
  }

  // XXX: Move!
  static VisitorState makeVisitorState(Tree target, Unifier unifier) {
    Context context = unifier.getContext();
    TreePath path = TreePath.getPath(context.get(JCCompilationUnit.class), target);
    return new VisitorState(context).withPath(path);
  }

  private boolean hasMatchingParameters(
      List<Type> params, List<Type> parameterTargetTypes, VisitorState state) {
    if (params.size() != parameterTargetTypes.size()) {
      return false;
    }

    for (int i = 0; i < params.size(); i++) {
      // XXX: For debugging purposes with Stephan:
      ImmutableList<Type> debugList = ImmutableList.of(params.get(i), parameterTargetTypes.get(i));
      // XXX: This does work though....
      // ASTHelpers.isSubtype(state.getTypeFromString(params.get(i).tsym.toString()),
      // state.getTypeFromString(parameterTargetTypes.get(i).tsym.toString()), state)
      if (!state.getTypes().isConvertible(params.get(i), parameterTargetTypes.get(i))) {
        return false;
      }
    }

    return true;
  }
}
