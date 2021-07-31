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

package com.google.errorprone.bugpatterns;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.migration.MigrationCodeTransformer;
import com.google.errorprone.refaster.CouldNotResolveImportException;
import com.google.errorprone.refaster.Inliner;
import com.google.errorprone.refaster.UType;
import com.google.errorprone.refaster.Unifier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.sun.tools.javac.code.Symbol.ClassSymbol;

@BugPattern(name = "RemoveMethod", summary = "Cleanup methods after migration", severity = ERROR)
public final class RemoveMethodsAfterMigration extends BugChecker implements MethodTreeMatcher {
  private static final Supplier<ImmutableList<MigrationCodeTransformer>> MIGRATION_TRANSFORMATIONS =
      Suppliers.memoize(RemoveMethodsAfterMigration::loadMigrationTransformer);

  private static ImmutableList<MigrationCodeTransformer> loadMigrationTransformer() {
    ImmutableList.Builder<MigrationCodeTransformer> migrations = new ImmutableList.Builder<>();
    ImmutableList<String> migrationDefinitionUris =
        ImmutableList.of(
            "../migration/src/main/java/com/google/errorprone/migration/templates/StringToInteger.migration",
            "../migration/src/main/java/com/google/errorprone/migration/templates/SingleToMono.migration");

    for (String migrationDefinitionUri : migrationDefinitionUris) {
      try (FileInputStream is = new FileInputStream(migrationDefinitionUri);
          ObjectInputStream ois = new ObjectInputStream(is)) {
        migrations.addAll(
            unwrap((CodeTransformer) ois.readObject())
                .filter(MigrationCodeTransformer.class::isInstance)
                .map(MigrationCodeTransformer.class::cast)
                .collect(toImmutableList()));
      } catch (IOException | ClassNotFoundException e) {
        // XXX: @Stephan, which exception to throw here?
        throw new IllegalStateException("Failed to read the Refaster migration template", e);
      }
    }
    return migrations.build();
  }

  private static Stream<CodeTransformer> unwrap(CodeTransformer codeTransformer) {
    if (!(codeTransformer instanceof CompositeCodeTransformer)) {
      return Stream.of(codeTransformer);
    }

    return ((CompositeCodeTransformer) codeTransformer)
        .transformers().stream().flatMap(RemoveMethodsAfterMigration::unwrap);
  }

  @Override
  public Description matchMethod(MethodTree methodTree, VisitorState state) {
    Inliner inliner = new Unifier(state.context).createInliner();
    ImmutableList<MigrationCodeTransformer> migrationDefinitions = MIGRATION_TRANSFORMATIONS.get();

    MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodTree);
    ClassSymbol enclosingClassSymbol = ASTHelpers.enclosingClass(methodSymbol);
    if (enclosingClassSymbol == null) {
      return Description.NO_MATCH;
    }

    Optional<MigrationCodeTransformer> suitableMigration =
        migrationDefinitions.stream()
            .filter(
                migration ->
                    ASTHelpers.isSameType(
                        inlineType(inliner, migration.typeFrom()),
                        methodSymbol.getReturnType(),
                        state))
            .findFirst();

    boolean isMigrated =
        enclosingClassSymbol
            .members()
            .getSymbolsByName(state.getName(methodTree.getName() + "_migrated"))
            .iterator()
            .hasNext();

    SuggestedFix deleteMethodFix = SuggestedFix.delete(methodTree);
    if (suitableMigration.isPresent()
        && isMigrated
        && SuggestedFixes.compilesWithFix(deleteMethodFix, state)) {
      return describeMatch(methodTree, deleteMethodFix);
    }
    return Description.NO_MATCH;
  }

  private static Type inlineType(Inliner inliner, UType uType) {
    try {
      return uType.inline(inliner);
    } catch (CouldNotResolveImportException e) {
      throw new IllegalStateException("Couldn't inline UType", e);
    }
  }
}
