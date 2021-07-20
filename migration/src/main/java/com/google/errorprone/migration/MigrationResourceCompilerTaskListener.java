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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.sun.tools.javac.code.Symbol.*;
import static com.sun.tools.javac.tree.JCTree.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.VisitorState;
import com.google.errorprone.refaster.RefasterRuleBuilderScanner;
import com.google.errorprone.refaster.UClassType;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

final class MigrationResourceCompilerTaskListener implements TaskListener {
  private final Context context;

  MigrationResourceCompilerTaskListener(Context context) {
    this.context = context;
  }

  @Override
  public void finished(TaskEvent taskEvent) {
    if (taskEvent.getKind() != TaskEvent.Kind.ANALYZE
        || JavaCompiler.instance(context).errorCount() > 0) {
      return;
    }

    ClassTree tree = JavacTrees.instance(context).getTree(taskEvent.getTypeElement());
    if (tree == null || !containsMigrationTemplates(tree)) {
      return;
    }

    ImmutableMap<ClassTree, CodeTransformer> rules = compileMigrationTemplates(tree);
    for (Map.Entry<ClassTree, CodeTransformer> rule : rules.entrySet()) {
      try {
        outputMigrationTransformer(rule.getValue(), getOutputFile(taskEvent, rule.getKey()));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private boolean containsMigrationTemplates(ClassTree tree) {
    return Boolean.TRUE.equals(
        new TreeScanner<Boolean, Void>() {
          @Override
          public Boolean visitAnnotation(AnnotationTree node, Void ctx) {
            Symbol sym = ASTHelpers.getSymbol(node);
            return (sym != null
                    && sym.getQualifiedName()
                        .contentEquals(MigrationTemplate.class.getCanonicalName()))
                || super.visitAnnotation(node, ctx);
          }

          @Override
          public Boolean reduce(Boolean r1, Boolean r2) {
            return Boolean.TRUE.equals(r1) || Boolean.TRUE.equals(r2);
          }
        }.scan(tree, null));
  }

  private ImmutableMap<ClassTree, CodeTransformer> compileMigrationTemplates(ClassTree tree) {
    Map<ClassTree, CodeTransformer> rules = new HashMap<>();
    new TreeScanner<Void, Context>() {
      @Override
      public Void visitClass(ClassTree node, Context ctx) {
        ImmutableList<ClassTree> migrationDefinitions =
            node.getMembers().stream()
                .filter(ClassTree.class::isInstance)
                .map(ClassTree.class::cast)
                .collect(toImmutableList());

        if (migrationDefinitions.size() != 2) {
          return super.visitClass(node, ctx);
        }

        ImmutableList<? extends Tree> beforeDefinition =
            migrationDefinitions.get(0).getMembers().stream()
                .filter(JCMethodDecl.class::isInstance)
                .filter(e -> !((JCMethodDecl) e).name.contentEquals("<init>"))
                .collect(toImmutableList());

        ImmutableList<? extends Tree> afterDefinition =
            migrationDefinitions.get(1).getMembers().stream()
                .filter(JCMethodDecl.class::isInstance)
                .filter(e -> !((JCMethodDecl) e).name.contentEquals("<init>"))
                .collect(toImmutableList());

        if (beforeDefinition.size() != 2 || afterDefinition.size() != 2) {
          return super.visitClass(node, ctx);
        }

        VisitorState state = VisitorState.createForUtilityPurposes(ctx);
        ImmutableList<MethodSymbol> fromMigrationDefinition =
            beforeDefinition.stream()
                .map(ASTHelpers::getSymbol)
                .filter(MethodSymbol.class::isInstance)
                .map(MethodSymbol.class::cast)
                .filter(
                    sym ->
                        ASTHelpers.hasAnnotation(sym, BeforeTemplate.class, state)
                            || ASTHelpers.hasAnnotation(sym, AfterTemplate.class, state))
                .collect(toImmutableList());

        ImmutableList<MethodSymbol> toMigrationDefinition =
            afterDefinition.stream()
                .map(ASTHelpers::getSymbol)
                .filter(MethodSymbol.class::isInstance)
                .map(MethodSymbol.class::cast)
                .filter(
                    sym ->
                        ASTHelpers.hasAnnotation(sym, BeforeTemplate.class, state)
                            || ASTHelpers.hasAnnotation(sym, AfterTemplate.class, state))
                .collect(toImmutableList());

        if (fromMigrationDefinition.size() != 2 || toMigrationDefinition.size() != 2 || migrationDefinitionsCorrect(fromMigrationDefinition, toMigrationDefinition)) {
          return super.visitClass(node, ctx);
        }

        UClassType beforeTemplateReturnType =
            UClassType.create(fromMigrationDefinition.get(0).getReturnType().toString());
        UClassType afterTemplateReturnType =
            UClassType.create(fromMigrationDefinition.get(1).getReturnType().toString());

        CodeTransformer migrationFrom =
            RefasterRuleBuilderScanner.extractRules(migrationDefinitions.get(0), ctx).stream()
                .findFirst()
                .get();
        CodeTransformer migrationTo =
            RefasterRuleBuilderScanner.extractRules(migrationDefinitions.get(1), ctx).stream()
                .findFirst()
                .get();
        MigrationCodeTransformer migrationCodeTransformer =
            MigrationCodeTransformer.create(
                migrationFrom, migrationTo, beforeTemplateReturnType, afterTemplateReturnType);
        rules.put(node, migrationCodeTransformer);
        // Here check for things. Is it our needed transformer?
        //        if (node.annotations == null)  then return.
        return super.visitClass(node, ctx);
      }
    }.scan(tree, context);
    return ImmutableMap.copyOf(rules);
  }

  private boolean migrationDefinitionsCorrect(ImmutableList<MethodSymbol> fromMigrationDefinition, ImmutableList<MethodSymbol> toMigrationDefinition) {
    // check here whether the param types are equal of both lists.
    // check whether the one converts to the other and back.
    // check that both lists have the following structure:
    // A A    (where A and B are types).
    // B A
  }

  private FileObject getOutputFile(TaskEvent taskEvent, ClassTree tree) throws IOException {
    String packageName =
        Optional.ofNullable(ASTHelpers.getSymbol(tree))
            .map(ASTHelpers::enclosingPackage)
            .map(PackageSymbol::toString)
            .orElse("");
    CharSequence className =
        Optional.ofNullable(ASTHelpers.getSymbol(tree))
            .map(MigrationResourceCompilerTaskListener::toSimpleFlatName)
            .orElseGet(tree::getSimpleName);
    String relativeName = className + ".migration";

    JavaFileManager fileManager = context.get(JavaFileManager.class);
    return fileManager.getFileForOutput(
        StandardLocation.CLASS_OUTPUT, packageName, relativeName, taskEvent.getSourceFile());
  }

  private static CharSequence toSimpleFlatName(ClassSymbol classSymbol) {
    Name flatName = classSymbol.flatName();
    int lastDot = flatName.lastIndexOf((byte) '.');
    return lastDot < 0 ? flatName : flatName.subSequence(lastDot + 1, flatName.length());
  }

  private static void outputMigrationTransformer(CodeTransformer rules, FileObject target)
      throws IOException {
    try (ObjectOutputStream output = new ObjectOutputStream(target.openOutputStream())) {
      output.writeObject(rules);
    }
  }
}
