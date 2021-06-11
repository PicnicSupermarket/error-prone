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
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

@AutoValue
public abstract class CType extends Types.SimpleVisitor<Choice<Unifier>, Unifier>
    implements Unifiable<Tree> {

  public static CType create(
      String fullyQualifiedClass, ImmutableList<UType> typeArguments, String name) {
    return new AutoValue_CType(fullyQualifiedClass, typeArguments, name);
  }

  abstract String fullyQualifiedClass();

  abstract ImmutableList<UType> typeArguments();

  // XXX: Show this to Stephan
  abstract String targetTypeParamName();

  @Override
  @Nullable
  public Choice<Unifier> visitType(Type t, @Nullable Unifier unifier) {
    return Choice.none();
  }

  @Override
  public Choice<Unifier> unify(Tree target, Unifier unifier) {
    VisitorState state = new VisitorState(unifier.getContext());
    Types types = unifier.types();
    Type targetType = state.getTypeFromString(fullyQualifiedClass());

    Type expressionType = unifier.getBinding(new UFreeIdent.Key(targetTypeParamName())).type;
    Type targetReturnType = types.findDescriptorType(targetType).getReturnType();

    if (types.isFunctionalInterface(expressionType)) {
      if (target instanceof LambdaExpressionTree) {
        Type lambdaReturnType = types.findDescriptorType(expressionType).getReturnType();
        boolean isReturnTypeConvertible = types.isConvertible(lambdaReturnType, targetReturnType);

        LambdaExpressionTree lambdaTree = (LambdaExpressionTree) target;
        boolean paramsWithinBounds = areParamsWithinBounds(state, lambdaTree, targetType);

        boolean throwsSignatureMatches = doesSignatureMatch(state, targetReturnType, lambdaTree);

        return Choice.condition(
            isReturnTypeConvertible && paramsWithinBounds && throwsSignatureMatches, unifier);
      } else if (target instanceof MemberReferenceTree) {
        MemberReferenceTree memberReferenceTree = (MemberReferenceTree) target;
//        List<Type> parameterTypes =
//            ((JCTree.JCMemberReference) memberReferenceTree).referentType.getParameterTypes();
//        Type returnType =
//            ((JCTree.JCMemberReference) memberReferenceTree).referentType.getReturnType();

        Symbol.MethodSymbol methodReferenceSymbol = ASTHelpers.getSymbol(memberReferenceTree);
        List<Type> parameters =
            methodReferenceSymbol.getParameters().stream()
                .map(param -> param.type)
                .collect(Collectors.toList());
        Type methodReferenceReturnType = methodReferenceSymbol.getReturnType();
        boolean isReturnTypeConvertible = types.isConvertible(methodReferenceReturnType, targetReturnType);

      }
    }

    return Choice.condition(types.isConvertible(expressionType, targetType), unifier);
  }

  private boolean doesSignatureMatch(
      VisitorState state, Type targetReturnType, LambdaExpressionTree lambdaTree) {
    // XXX: Discuss with Stephan, the first is a set, and the other isn't. How to handle this?
    List<Type> targetThrownTypes = targetReturnType.getThrownTypes();
    ImmutableSet<Type> thrownExceptions =
        ASTHelpers.getThrownExceptions(lambdaTree.getBody(), state);
    return targetThrownTypes.stream()
        .allMatch(
            targetThrows ->
                thrownExceptions.stream()
                    .anyMatch(t -> ASTHelpers.isSubtype(targetThrows, t, state)));
  }

  private boolean areParamsWithinBounds(
      VisitorState state, LambdaExpressionTree lambdaTree, Type targetType) {
    List<? extends VariableTree> lambdaParameters = lambdaTree.getParameters();
    List<Type> targetParameterTypes =
        state.getTypes().findDescriptorType(targetType).getParameterTypes();

    if (lambdaParameters.size() != targetParameterTypes.size()) {
      return false;
    }

    for (int i = 0; i < lambdaParameters.size(); i++) {
      Type paramType = ((JCTree.JCVariableDecl) lambdaParameters.get(i)).getType().type;
      Type targetParamType = targetParameterTypes.get(i);

      if (!state.getTypes().isConvertible(targetParamType.getLowerBound(), paramType)
          && state.getTypes().isConvertible(paramType, targetParamType.getUpperBound())) {
        return false;
      }
    }

    return true;
  }
}
