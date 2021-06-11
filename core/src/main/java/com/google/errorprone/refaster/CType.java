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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@AutoValue
public abstract class CType extends Types.SimpleVisitor<Choice<Unifier>, Unifier>
    implements Unifiable<Tree> {
  public static CType create(String fullyQualifiedClass, ImmutableList<UType> typeArguments) {
    return new AutoValue_CType(fullyQualifiedClass, typeArguments);
  }

  abstract String fullyQualifiedClass();

  abstract ImmutableList<UType> typeArguments();

  @Override
  @Nullable
  public Choice<Unifier> visitType(Type t, @Nullable Unifier unifier) {
    return Choice.none();
  }

  @Override
  public Choice<Unifier> unify(Tree tree, Unifier unifier) {
    VisitorState state = new VisitorState(unifier.getContext());
    Types types = unifier.types();
    Type targetType = state.getTypeFromString(fullyQualifiedClass());

    Type expressionType = ASTHelpers.getType(tree);
    Type targetReturnType = types.findDescriptorType(targetType).getReturnType();

    if (types.isFunctionalInterface(expressionType)) {
      if (tree instanceof LambdaExpressionTree) {
        LambdaExpressionTree lambdaTree = (LambdaExpressionTree) tree;

        Tree lambdaBody = lambdaTree.getBody();
        Type x = null;
        switch (lambdaTree.getBodyKind()) {
          case EXPRESSION:
            x = ASTHelpers.getType(lambdaBody);
            break;
          case STATEMENT:
            ReturnTypeScanner returnTypeScanner = new ReturnTypeScanner();
            returnTypeScanner.scan(lambdaBody, null);
            // XXX: get LUB of these types.
            List<Type> returnTypes = returnTypeScanner.getReturnTypesOfLambda();
            // XXX: This doesn't work when one of the two is a primitive. See `glb` implementation.
            x = state.getTypes().glb(com.sun.tools.javac.util.List.from(returnTypes));
            x = state.getTypes().lub(com.sun.tools.javac.util.List.from(returnTypes));
            Type x1 = x;
        }

        Type lambdaReturnType = types.findDescriptorType(expressionType).getReturnType();
        boolean isReturnTypeConvertible = types.isConvertible(lambdaReturnType, targetReturnType);

        List<? extends VariableTree> lambdaParameters = lambdaTree.getParameters();
        ImmutableList<Type> params =
            lambdaParameters.stream().map(ASTHelpers::getType).collect(toImmutableList());
        boolean paramsWithinBounds = areParamsWithinBounds(state, params, targetType);

        // XXX: Rename
        // XXX: Don't pass in the *return* type.
        boolean throwsSignatureMatches =
            doesThrowSignatureMatch(state, targetReturnType, lambdaBody);

        // XXX: Performance: short-circuit. Might come naturally once we factor this stuff out in a
        // separate method (early returns).
        return Choice.condition(
            isReturnTypeConvertible && paramsWithinBounds && throwsSignatureMatches, unifier);
      } else if (tree instanceof MemberReferenceTree) {
        MemberReferenceTree memberReferenceTree = (MemberReferenceTree) tree;

        Symbol.MethodSymbol methodReferenceSymbol = ASTHelpers.getSymbol(memberReferenceTree);
        ImmutableList<Type> params =
            methodReferenceSymbol.getParameters().stream()
                .map(param -> param.type)
                .collect(toImmutableList());
        boolean paramsWithinBounds = areParamsWithinBounds(state, params, targetType);

        Type methodReferenceReturnType = methodReferenceSymbol.getReturnType();
        boolean isReturnTypeConvertible =
            types.isConvertible(methodReferenceReturnType, targetReturnType);

        boolean throwsSignatureMatches =
            doesThrowSignatureMatch(state, targetReturnType, memberReferenceTree);
      }
    }

    return Choice.condition(types.isConvertible(expressionType, targetType), unifier);
  }

  private boolean doesThrowSignatureMatch(VisitorState state, Type targetReturnType, Tree tree) {
    // XXX: Discuss with Stephan, the first is a set, and the other isn't. How to handle this?
    List<Type> targetThrownTypes = targetReturnType.getThrownTypes();
    ImmutableSet<Type> thrownExceptions = ASTHelpers.getThrownExceptions(tree, state);
    // XXX: Check: might be the other way around.
    return targetThrownTypes.stream()
        .allMatch(
            targetThrows ->
                thrownExceptions.stream()
                    .anyMatch(t -> ASTHelpers.isSubtype(targetThrows, t, state)));
  }

  // XXX: Make `VisitorState` last param in all cases?
  // XXX: Pass in just `Types`?
  // XXX: Pass in `targetParameterTypes`.
  private boolean areParamsWithinBounds(
      VisitorState state, ImmutableList<Type> parameterTypes, Type targetType) {
    List<Type> targetParameterTypes =
        state.getTypes().findDescriptorType(targetType).getParameterTypes();

    if (parameterTypes.size() != targetParameterTypes.size()) {
      return false;
    }

    for (int i = 0; i < parameterTypes.size(); i++) {
      Type boxedParamTypeOrType = state.getTypes().boxedTypeOrType(parameterTypes.get(i));
      Type targetParamType = targetParameterTypes.get(i);

      if (!state.getTypes().isConvertible(targetParamType.getLowerBound(), boxedParamTypeOrType)
          || !state
              .getTypes()
              .isConvertible(boxedParamTypeOrType, targetParamType.getUpperBound())) {
        return false;
      }
    }

    return true;
  }

  private static final class ReturnTypeScanner extends TreeScanner<Void, Void> {
    List<Type> returnTypes = new ArrayList<>();

    public List<Type> getReturnTypesOfLambda() {
      return returnTypes;
    }

    @Override
    public Void visitReturn(ReturnTree node, Void unused) {
      returnTypes.add(ASTHelpers.getType(node.getExpression()));
      return super.visitReturn(node, unused);
    }
  }
}
