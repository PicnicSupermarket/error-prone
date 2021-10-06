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

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.refaster.CouldNotResolveImportException;
import com.google.errorprone.refaster.Inliner;
import com.google.errorprone.refaster.UType;
import com.google.errorprone.refaster.Unifier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.Name;
import java.util.Optional;
import java.util.function.Supplier;

@AutoService(BugChecker.class)
@BugPattern(
    name = "RemoveOldMethods",
    summary = "Try to do the final steps for the migration.",
    severity = ERROR)
public class RemoveOldMethods extends BugChecker implements MethodTreeMatcher {

  private static final Supplier<ImmutableList<MigrationCodeTransformer>> MIGRATION_TRANSFORMATIONS =
      MigrationTransformersProvider.MIGRATION_TRANSFORMATIONS;

  @Override
  public Description matchMethod(MethodTree methodTree, VisitorState state) {
    ImmutableList<MigrationCodeTransformer> migrationDefinitions = MIGRATION_TRANSFORMATIONS.get();
    Inliner inliner = new Unifier(state.context).createInliner();
    Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodTree);
    Symbol.ClassSymbol enclosingClassSymbol = ASTHelpers.enclosingClass(methodSymbol);

    Optional<MigrationCodeTransformer> suitableMigration =
        migrationDefinitions.stream()
            .filter(
                migration ->
                    TypeMigrationHelper.isMethodTypeUndesiredMigrationType(
                        inliner.types(),
                        methodSymbol.getReturnType(),
                        inlineType(inliner, migration.typeFrom()),
                        state))
            .findFirst();

    if (!suitableMigration.isPresent()
        && !isMethodAlreadyMigratedInEnclosingClass(
            methodTree, enclosingClassSymbol, methodSymbol.getSimpleName(), state)) {
      return Description.NO_MATCH;
    }

    if (enclosingClassSymbol.isInterface()) {
      if (methodTree.getName().toString().contains("_migrated")) {

      } else {
        return describeMatch(methodTree, SuggestedFix.delete(methodTree));
      }
      // Remove the old.
      // Remove default impl from other method.
    } else {
      return describeMatch(methodTree, SuggestedFix.delete(methodTree));
    }

    return Description.NO_MATCH;
  }

  private static boolean isMethodAlreadyMigratedInEnclosingClass(
      MethodTree methodTree,
      Symbol.ClassSymbol enclosingClassSymbol,
      Name methodName,
      VisitorState state) {
    return enclosingClassSymbol
            .members()
            .getSymbolsByName(state.getName(methodName + "_migrated"))
            .iterator()
            .hasNext()
        && hasAnnotation(methodTree, Deprecated.class, state);
  }

  private static Type inlineType(Inliner inliner, UType uType) {
    try {
      return uType.inline(new Inliner(inliner.getContext(), inliner.bindings));
    } catch (CouldNotResolveImportException e) {
      throw new IllegalStateException(
          "Couldn't inline UType"
              + uType.getClass()
              + ";"
              + uType
              + ". Did you add the correct dependencies to the pom.xml?",
          e);
    }
  }
}
