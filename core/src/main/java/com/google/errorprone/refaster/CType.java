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
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    if (targetType == null) {
      return Choice.none();
    }

    Inliner inliner = unifier.createInliner();
    ImmutableList<Type> inlinedTargetArguments =
        typeArguments().stream().map(t -> toType(t, inliner)).collect(toImmutableList());

    Type expressionType = ASTHelpers.getType(tree);
    Type targetReturnType = types.findDescriptorType(targetType).getReturnType();

    if (types.isFunctionalInterface(expressionType)) {
      if (tree instanceof LambdaExpressionTree) {
        LambdaExpressionTree lambdaTree = (LambdaExpressionTree) tree;

        Type lambdaReturnType = targetReturnType;
        if (lambdaTree.getParameters().size() < inlinedTargetArguments.size()) {
          lambdaReturnType = inlinedTargetArguments.get(inlinedTargetArguments.size() - 1);
        }
        boolean doesReturnTypeMatch =
            doesReturnTypeOfLambdaMatch(lambdaTree, lambdaReturnType, types);

        List<? extends VariableTree> lambdaParameters = lambdaTree.getParameters();
        int size = types.findDescriptorType(targetType).getParameterTypes().size();
        ImmutableList<Type> params =
            lambdaParameters.stream().map(ASTHelpers::getType).collect(toImmutableList());
        boolean paramsWithinBounds =
            areParamsWithinBounds(
                inlinedTargetArguments.subList(0, size), params, types, unifier.createInliner());

        ImmutableSet<Type> thrownExceptions =
            ASTHelpers.getThrownExceptions(lambdaTree.getBody(), state);
        boolean methodThrowsMatches =
            doesMethodThrowsMatches(thrownExceptions.asList(), targetType.getThrownTypes(), state);

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

        // XXX: Discuss with Stephan, how do we want to handle primitive for now? -> look for
        // `allTypesPrimitive`
        boolean doesReturnTypeMatch =
            types.isConvertible(methodReferenceSymbol.getReturnType(), targetReturnType);

        ImmutableList<Type> params =
            methodReferenceSymbol.getParameters().stream()
                .map(param -> param.type)
                .collect(toImmutableList());

        boolean paramsWithinBounds =
            areParamsWithinBounds(
                types.findDescriptorType(targetType).getParameterTypes(),
                params,
                types,
                unifier.createInliner());

        boolean throwsSignatureMatches =
            doesMethodThrowsMatches(
                methodReferenceSymbol.getThrownTypes(), targetType.getThrownTypes(), state);

        return Choice.condition(
            doesReturnTypeMatch && paramsWithinBounds && throwsSignatureMatches, unifier);
      }
    }

    return Choice.condition(types.isConvertible(expressionType, targetType), unifier);
  }

  private Type toType(UType utype, Inliner inliner) {
    try {
      return utype.inline(inliner);
    } catch (CouldNotResolveImportException e) {
      // XXX: Fix this
      throw new RuntimeException();
    }
  }

  private boolean doesReturnTypeOfLambdaMatch(
      LambdaExpressionTree lambdaTree, Type targetReturnType, Types types) {
    Tree lambdaBody = lambdaTree.getBody();
    Type lambdaReturnType = null;
    switch (lambdaTree.getBodyKind()) {
      case EXPRESSION:
        lambdaReturnType = ASTHelpers.getType(lambdaBody);
        break;
      case STATEMENT:
        ReturnTypeScanner returnTypeScanner = new ReturnTypeScanner();
        returnTypeScanner.scan(lambdaBody, null);

        boolean allTypesPrimitive =
            returnTypeScanner.getReturnTypesOfTree().stream().allMatch(Type::isPrimitive);
        if (allTypesPrimitive) {
          // If are all primitive types: JLS 4.10.1. Subtyping among Primitive Types
          Optional<Type> reduced =
              returnTypeScanner.getReturnTypesOfTree().stream()
                  .reduce((Type a, Type b) -> types.isSubtype(a, b) ? b : a);
          lambdaReturnType = reduced.get();
        } else {

          // XXX: Should we write own logic for this? Or do we decide to go for boxed types?
          // XXX: This doesn't work when one of the two is a primitive. See `glb` implementation.
          ImmutableList<Type> returnTypes =
              returnTypeScanner.getReturnTypesOfTree().stream()
                  .map(types::boxedTypeOrType)
                  .collect(toImmutableList());
          lambdaReturnType = types.lub(com.sun.tools.javac.util.List.from(returnTypes));
        }
    }

    boolean b;
    if (targetReturnType
        .isPrimitive()) { // XXX: Perhaps add a case where both are primitive, and one case where
                          // only one is and they are convertible. Perhaps look in JLS?
      b = types.isSubtype(lambdaReturnType, targetReturnType);
    } else {
      b =
          types.isSubtype(targetReturnType.getLowerBound(), lambdaReturnType)
              && types.isSubtype(lambdaReturnType, targetReturnType.getUpperBound());
    }

    return b;
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

  private boolean areParamsWithinBounds(
      List<Type> targetParameterTypes,
      ImmutableList<Type> parameterTypes,
      Types types,
      Inliner inliner) {
    if (parameterTypes.size() != targetParameterTypes.size()) {
      return false;
    }

    for (int i = 0; i < parameterTypes.size(); i++) {
      Type boxedParamTypeOrType = types.boxedTypeOrType(parameterTypes.get(i));
      Type targetParamType = targetParameterTypes.get(i);

      //      Type type = null;
      //      try {
      //        type = typeArguments().get(0).inline(inliner);
      //      } catch (CouldNotResolveImportException e) {
      //        e.printStackTrace();
      //      }
      // XXX: Here we should look at the paramtypes of the generics.
      if (!types.isConvertible(targetParamType.getLowerBound(), boxedParamTypeOrType)
          && !types.isConvertible(boxedParamTypeOrType, targetParamType.getUpperBound())) {
        return false;
      }
    }

    return true;
  }

  private static final class ReturnTypeScanner extends TreeScanner<Void, Void> {
    private final List<Type> returnTypes = new ArrayList<>();

    public List<Type> getReturnTypesOfTree() {
      return returnTypes;
    }

    @Override
    public Void visitReturn(ReturnTree tree, Void unused) {
      returnTypes.add(ASTHelpers.getType(tree.getExpression()));
      return super.visitReturn(tree, unused);
    }
  }
}
