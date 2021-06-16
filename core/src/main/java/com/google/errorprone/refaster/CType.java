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
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.List;

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
    VisitorState state = VisitorState.createForUtilityPurposes(unifier.getContext());
    Types types = unifier.types();
    Type targetType = state.getTypeFromString(fullyQualifiedClass());
    if (targetType == null) {
      return Choice.none();
    }

    Type expressionType = ASTHelpers.getType(tree);

    Inliner inliner = unifier.createInliner();
    List<Type> inlinedTargetTypeArguments = getInlinedTypeArguments(inliner, typeArguments());

    Type improvedTargetType =
        types.subst(targetType, targetType.getTypeArguments(), inlinedTargetTypeArguments);
    Type improvedTargetTypeDescriptorType = types.findDescriptorType(improvedTargetType);

    if (types.isFunctionalInterface(expressionType)) {
      if (tree instanceof LambdaExpressionTree) {
        LambdaExpressionTree lambdaTree = (LambdaExpressionTree) tree;

        boolean doesReturnTypeMatch =
            doesReturnTypeOfLambdaMatch(
                lambdaTree, improvedTargetTypeDescriptorType.getReturnType(), types);

        java.util.List<? extends VariableTree> lambdaParameters = lambdaTree.getParameters();
        ImmutableList<Type> params =
            lambdaParameters.stream().map(ASTHelpers::getType).collect(toImmutableList());
        boolean paramsWithinBounds =
            areParamsWithinBounds(
                improvedTargetTypeDescriptorType.getParameterTypes(), params, state.getTypes());

        ImmutableSet<Type> thrownExceptions =
            ASTHelpers.getThrownExceptions(lambdaTree.getBody(), state);
        boolean methodThrowsMatches =
            doesMethodThrowsMatches(
                List.from(thrownExceptions), improvedTargetType.getThrownTypes(), state);

        // XXX: Performance: short-circuit. Might come naturally once we factor this stuff out in a
        // separate method (early returns).
        return Choice.condition(
            doesReturnTypeMatch && paramsWithinBounds && methodThrowsMatches, unifier);
      } else if (tree instanceof MemberReferenceTree) {
        MemberReferenceTree memberReferenceTree = (MemberReferenceTree) tree;
        MethodSymbol methodReferenceSymbol = ASTHelpers.getSymbol(memberReferenceTree);
        if (methodReferenceSymbol == null) {
          return Choice.none();
        }

        // XXX: Discuss with Stephan, how do we want to handle primitive for now? -> Allow
        // autoboxing, more "default" way of thinking for people.
        boolean doesReturnTypeMatch =
            types.isConvertible(
                methodReferenceSymbol.getReturnType(),
                improvedTargetTypeDescriptorType.getReturnType());

        ImmutableList<Type> params =
            methodReferenceSymbol.getParameters().stream()
                .map(param -> param.type)
                .collect(toImmutableList());
        boolean paramsWithinBounds =
            areParamsWithinBounds(improvedTargetType.getParameterTypes(), params, types);

        boolean throwsSignatureMatches =
            doesMethodThrowsMatches(
                methodReferenceSymbol.getThrownTypes(),
                improvedTargetTypeDescriptorType.getThrownTypes(),
                state);

        return Choice.condition(
            doesReturnTypeMatch && paramsWithinBounds && throwsSignatureMatches, unifier);
      }
    }

    return Choice.none();
  }

  private static List<Type> getInlinedTypeArguments(Inliner inliner, ImmutableList<UType> types) {
    try {
      return inliner.inlineList(types);
    } catch (CouldNotResolveImportException e) {
      throw new IllegalArgumentException("Unsupported argument for getInlinedTypeArguments");
    }
  }

  private boolean doesReturnTypeOfLambdaMatch(
      LambdaExpressionTree lambdaTree, Type targetReturnType, Types types) {
    Tree lambdaBody = lambdaTree.getBody();
    Type lambdaReturnType = null;
    switch (lambdaTree.getBodyKind()) {
      case EXPRESSION:
        lambdaReturnType = types.boxedTypeOrType(ASTHelpers.getType(lambdaBody));
        break;
      case STATEMENT:
        ReturnTypeScanner returnTypeScanner = new ReturnTypeScanner();
        returnTypeScanner.scan(lambdaBody, null);
        List<Type> returnTypes =
            returnTypeScanner.getReturnTypesOfTree().stream()
                .map(types::boxedTypeOrType)
                .collect(List.collector());

        lambdaReturnType = types.lub(returnTypes);
    }

    return types.isSubtype(targetReturnType.getLowerBound(), lambdaReturnType)
        && types.isSubtype(lambdaReturnType, targetReturnType.getUpperBound());
  }

  private boolean doesMethodThrowsMatches(
      List<Type> thrownExceptions, List<Type> targetThrownTypes, VisitorState state) {

    return thrownExceptions.stream()
        .allMatch(
            thrownException ->
                targetThrownTypes.stream()
                    .anyMatch(
                        targetException ->
                            ASTHelpers.isSubtype(thrownException, targetException, state)));
  }

  private static boolean areParamsWithinBounds(
      List<Type> targetParameterTypes, ImmutableList<Type> parameterTypes, Types types) {
    if (parameterTypes.size() != targetParameterTypes.size()) {
      return false;
    }

    for (int i = 0; i < parameterTypes.size(); i++) {
      Type boxedParamTypeOrType = types.boxedTypeOrType(parameterTypes.get(i));
      // XXX: Should this one also be boxed?
      Type targetParamType = targetParameterTypes.get(i);

      if (!types.isSubtype(targetParamType.getLowerBound(), boxedParamTypeOrType)
          && !types.isSubtype(boxedParamTypeOrType, targetParamType.getUpperBound())) {
        return false;
      }
    }

    return true;
  }

  private static final class ReturnTypeScanner extends TreeScanner<Void, Void> {
    private final List<Type> returnTypes = List.nil();

    public List<Type> getReturnTypesOfTree() {
      return returnTypes;
    }

    @Override
    public Void visitLambdaExpression(LambdaExpressionTree node, Void unused) {
      return null;
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
      return null;
    }

    @Override
    public Void visitReturn(ReturnTree tree, Void unused) {
      returnTypes.add(ASTHelpers.getType(tree.getExpression()));
      return super.visitReturn(tree, unused);
    }
  }
}
