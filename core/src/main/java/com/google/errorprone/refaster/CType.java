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

// XXX: Add support for the `@NoAutoBoxing` annotation.
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

    Type expressionType = ASTHelpers.getType(tree);
    Type targetType = getTargetType(unifier, types, state);

    if (types.isFunctionalInterface(expressionType)) {
      if (tree instanceof LambdaExpressionTree) {
        LambdaExpressionTree lambdaTree = (LambdaExpressionTree) tree;

        boolean doesReturnTypeMatch =
            doesLambdaReturnTypeMatch(lambdaTree, targetType.getReturnType(), types);

        java.util.List<? extends VariableTree> lambdaParameters = lambdaTree.getParameters();
        ImmutableList<Type> params =
            lambdaParameters.stream().map(ASTHelpers::getType).collect(toImmutableList());
        boolean paramsWithinBounds =
            areParamsWithinBounds(params, targetType.getParameterTypes(), state.getTypes());

        ImmutableSet<Type> thrownExceptions =
            ASTHelpers.getThrownExceptions(lambdaTree.getBody(), state);
        boolean methodThrowsMatches =
            doesMethodThrowsMatches(
                List.from(thrownExceptions), targetType.getThrownTypes(), state);

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

        boolean doesReturnTypeMatch =
            isSubtypeOrWithinBounds(
                methodReferenceSymbol.getReturnType(), targetType.getReturnType(), types);

        ImmutableList<Type> params =
            methodReferenceSymbol.getParameters().stream()
                .map(param -> param.type)
                .collect(toImmutableList());
        boolean paramsWithinBounds =
            areParamsWithinBounds(params, targetType.getParameterTypes(), types);

        boolean throwsSignatureMatches =
            doesMethodThrowsMatches(
                methodReferenceSymbol.getThrownTypes(), targetType.getThrownTypes(), state);

        return Choice.condition(
            doesReturnTypeMatch && paramsWithinBounds && throwsSignatureMatches, unifier);
      }
    }

    return Choice.none();
  }

  // XXX: Discuss naming. `getSubstitutedTargetType`? `extractSubstitutedTargetType`?
  private Type getTargetType(Unifier unifier, Types types, VisitorState state) {
    Inliner inliner = unifier.createInliner();
    List<Type> inlinedTargetTypeArguments = inlineUTypes(inliner, typeArguments());

    Type originalTargetType = state.getTypeFromString(fullyQualifiedClass());
    if (originalTargetType == null) {
      throw new IllegalArgumentException(
          "Can't create type from the fullyQualifiedClass: " + fullyQualifiedClass());
    }

    Type targetTypeWithSubstitutedTypeArguments =
        types.subst(
            originalTargetType, originalTargetType.getTypeArguments(), inlinedTargetTypeArguments);
    return types.findDescriptorType(targetTypeWithSubstitutedTypeArguments);
  }

  private static List<Type> inlineUTypes(Inliner inliner, ImmutableList<UType> types) {
    try {
      return inliner.inlineList(types);
    } catch (CouldNotResolveImportException e) {
      throw new IllegalArgumentException("Unsupported argument for inlineUTypes");
    }
  }

  private static boolean doesLambdaReturnTypeMatch(
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
        List<Type> returnTypes =
            returnTypeScanner.getReturnTypes().stream()
                .map(types::boxedTypeOrType)
                .collect(List.collector());

        lambdaReturnType = types.lub(returnTypes);
    }

    return isSubtypeOrWithinBounds(lambdaReturnType, targetReturnType, types);
  }

  private static boolean isSubtypeOrWithinBounds(Type exprType, Type targetType, Types types) {
    exprType = types.boxedTypeOrType(exprType);
    targetType = types.boxedTypeOrType(targetType);

    // XXX: I think there is a better way to check this
    if (targetType.getLowerBound() == null && targetType.getUpperBound() == null) {
      return types.isSubtype(exprType, targetType);
    }
    return types.isSubtype(targetType.getLowerBound(), exprType)
        && types.isSubtype(exprType, targetType.getUpperBound());
  }

  private static boolean doesMethodThrowsMatches(
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
      ImmutableList<Type> parameterTypes, List<Type> targetParameterTypes, Types types) {
    if (parameterTypes.size() != targetParameterTypes.size()) {
      return false;
    }

    for (int i = 0; i < parameterTypes.size(); i++) {
      if (!isSubtypeOrWithinBounds(parameterTypes.get(i), targetParameterTypes.get(i), types)) {
        return false;
      }
    }
    return true;
  }

  private static final class ReturnTypeScanner extends TreeScanner<Void, Void> {
    private final List<Type> returnTypes = List.nil();

    public List<Type> getReturnTypes() {
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
      returnTypes.append(ASTHelpers.getType(tree.getExpression()));
      return super.visitReturn(tree, unused);
    }
  }
}
