/*
 * Copyright 2021 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.migration;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.google.errorprone.util.MoreAnnotations.asStringValue;
import static com.google.errorprone.util.MoreAnnotations.getValue;
import static com.sun.tools.javac.code.Symbol.*;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.refaster.CouldNotResolveImportException;
import com.google.errorprone.refaster.Inliner;
import com.google.errorprone.refaster.UType;
import com.google.errorprone.refaster.Unifier;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@BugPattern(
    name = "InlineMockitoStatements",
    summary = "Migrate Mockito statements that call a method annotated with `@InlineMe`.",
    severity = WARNING)
public class InlineMockitoStatements extends BugChecker implements MethodInvocationTreeMatcher {
  private static final Supplier<ImmutableList<MigrationCodeTransformer>> MIGRATION_TRANSFORMATIONS =
      Suppliers.memoize(MigrationTransformersProvider::loadMigrationTransformers);

  private static final String INLINE_ME = "com.google.errorprone.annotations.InlineMe";

  private static final Matcher<ExpressionTree> MOCKITO_MATCHER_WHEN =
      staticMethod().onClass("org.mockito.Mockito").named("when");

  private static final Matcher<ExpressionTree> MOCKITO_MATCHER_DO_WHEN =
      anyOf(instanceMethod().onDescendantOf("org.mockito.stubbing.Stubber").named("when"));
  //          staticMethod().onClass("org.mockito.Mockito").named("verify"));

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    ImmutableList<MigrationCodeTransformer> migrationDefinitions = MIGRATION_TRANSFORMATIONS.get();

    if (MOCKITO_MATCHER_WHEN.matches(tree, state)) {
      Tree grandParent = state.getPath().getParentPath().getParentPath().getLeaf();
      List<? extends ExpressionTree> thenReturnArguments =
          ((MethodInvocationTree) grandParent).getArguments();
      ExpressionTree whenArgument = Iterables.getOnlyElement(tree.getArguments());
      Symbol whenSymbol = getSymbol(whenArgument);
      if (methodWithoutInlineOrMigratedReplacement(whenSymbol, state)
          || !(whenArgument instanceof MethodInvocationTree)) {
        return Description.NO_MATCH;
      }

      Optional<MigrationCodeTransformer> suitableMigration =
          getSuitableMigrationForMethod(whenSymbol, migrationDefinitions, state);
      if (!suitableMigration.isPresent()) {
        return Description.NO_MATCH;
      }

      List<Description> descriptions =
          thenReturnArguments.stream()
              .map(e -> getMigrationReplacementForNormalMethod(e, suitableMigration.get(), state))
              .collect(Collectors.toList());

      SuggestedFix.Builder fix = SuggestedFix.builder();
      descriptions.add(
          describeMatch(
              whenArgument,
              SuggestedFixes.renameMethodInvocation(
                  (MethodInvocationTree) whenArgument,
                  whenSymbol.getQualifiedName() + "_migrated",
                  state)));
      descriptions.add(describeMatch(tree, SuggestedFix.prefixWith(tree, grandParent + ";\n")));
      descriptions.forEach(d -> fix.merge((SuggestedFix) getOnlyElement(d.fixes)));

      return describeMatch(tree, fix.build());
    } else if (MOCKITO_MATCHER_DO_WHEN.matches(tree, state)) {
      Tree grandParent = state.getPath().getParentPath().getParentPath().getLeaf();
      Tree.Kind kind = grandParent.getKind();
    }
    return Description.NO_MATCH;
  }

  private Optional<MigrationCodeTransformer> getSuitableMigrationForMethod(
      Symbol whenSymbol,
      ImmutableList<MigrationCodeTransformer> migrationDefinitions,
      VisitorState state) {
    MethodSymbol whenMethodSymbol = (MethodSymbol) whenSymbol;
    Inliner inliner = new Unifier(state.context).createInliner();
    return migrationDefinitions.stream()
        .filter(
            migration ->
                TypeMigrationHelper.isMethodTypeUndesiredMigrationType(
                    inliner.types(),
                    whenMethodSymbol.getReturnType(),
                    inlineType(inliner, migration.typeFrom()),
                    state))
        .findFirst();
  }

  private boolean methodWithoutInlineOrMigratedReplacement(Symbol whenSymbol, VisitorState state) {
    if (!hasAnnotation(whenSymbol, INLINE_ME, state)) {
      return true;
    }
    Attribute.Compound inlineMe =
        whenSymbol.getRawAttributes().stream()
            .filter(a -> a.type.tsym.getQualifiedName().contentEquals(INLINE_ME))
            .collect(onlyElement());
    String replacement = asStringValue(getValue(inlineMe, "replacement").get()).get();

    return !replacement.contains("_migrated");
  }

  private static Type inlineType(Inliner inliner, UType uType) {
    // XXX: Explain new `Inliner` creation.
    try {
      return uType.inline(new Inliner(inliner.getContext(), inliner.bindings));
    } catch (CouldNotResolveImportException e) {
      throw new IllegalStateException("Couldn't inline UType" + uType.getClass() + ";" + uType, e);
    }
  }

  private Description getMigrationReplacementForNormalMethod(
      Tree tree, MigrationCodeTransformer migrationCodeTransformer, VisitorState state) {
    JCTree.JCCompilationUnit compilationUnit =
        (JCTree.JCCompilationUnit) state.getPath().getCompilationUnit();
    TreePath compUnitTreePath = new TreePath(compilationUnit);
    TreePath methodPath = new TreePath(compUnitTreePath, tree);

    java.util.List<Description> matches = new ArrayList<>();
    migrationCodeTransformer.transformFrom().apply(methodPath, state.context, matches::add);

    if (matches.isEmpty()) {
      return Description.NO_MATCH;
    }
    return matches.get(0);
  }
}
