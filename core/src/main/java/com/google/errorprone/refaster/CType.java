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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;

import javax.annotation.Nullable;
import java.util.List;

@AutoValue
public abstract class CType extends Types.SimpleVisitor<Choice<Unifier>, Unifier>
    implements Unifiable<Tree> {

  public static CType create(String fullyQualifiedClass, ImmutableList<UType> typeArguments, String name) {
    return new AutoValue_CType(fullyQualifiedClass, typeArguments, name);
  }

  abstract String fullyQualifiedClass();

  abstract ImmutableList<UType> typeArguments();

  abstract String name();

  @Override
  @Nullable
  public Choice<Unifier> visitType(Type t, @Nullable Unifier unifier) {
    return Choice.none();
  }

  @Override
  public Choice<Unifier> unify(Tree target, Unifier unifier) {
    VisitorState state = new VisitorState(unifier.getContext());
    Type typeFromString = state.getTypeFromString(fullyQualifiedClass());

    Type expressionType = unifier.getBinding(new UFreeIdent.Key(name())).type;
//    Type type = ASTHelpers.getType(target);
//    Type targetType = ASTHelpers.getType(target);

    if (unifier.types().isFunctionalInterface(expressionType)) {
      if (target instanceof LambdaExpressionTree) {
        Type lambdaReturnType = state.getTypes().findDescriptorType(expressionType).getReturnType();
        Type targetReturnType = unifier.types().findDescriptorType(typeFromString).getReturnType();
        boolean convertible = unifier.types().isConvertible(lambdaReturnType, targetReturnType);
        // XXX: Here we can use state.getTypes().findDescriptorType(targetType) #args and #throws.

        LambdaExpressionTree lambdaTree = (LambdaExpressionTree) target;

        List<? extends VariableTree> lambdaParameters = lambdaTree.getParameters();
        List<Type> targetParameterTypes = unifier.types().findDescriptorType(typeFromString).getParameterTypes();
        hasMatchingParameters(lambdaParameters, targetParameterTypes, state);


        ImmutableSet<Type> thrownExceptions = ASTHelpers.getThrownExceptions(lambdaTree.getBody(), state);
      }

    }

    return Choice.condition(unifier.types().isConvertible(expressionType, typeFromString), unifier);
  }


  private boolean hasMatchingParameters(
          List<? extends VariableTree> params, List<Type> parameterTargetTypes, VisitorState state) {
    if (params.size() != parameterTargetTypes.size()) {
      return false;
    }

    for (int i = 0; i < params.size(); i++) {

//      if (!state.getTypes().isConvertible(((JCTree.JCVariableDecl) params.get(0)).getType().type, parameterTargetTypes.get(i))) {
//        return false;
//      }
    }

    return true;
  }
}
