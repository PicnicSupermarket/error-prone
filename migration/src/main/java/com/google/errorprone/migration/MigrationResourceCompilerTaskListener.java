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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import com.google.errorprone.refaster.RefasterRuleBuilderScanner;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    ImmutableListMultimap<ClassTree, CodeTransformer> rules = compileMigrationTemplates(tree);
    for (Map.Entry<ClassTree, List<CodeTransformer>> rule : Multimaps.asMap(rules).entrySet()) {
      try {
        outputCodeTransformers(rule.getValue(), getOutputFile(taskEvent, rule.getKey()));
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
                    && sym.getQualifiedName().contentEquals(MigrationTemplate.class.getCanonicalName()))
                || super.visitAnnotation(node, ctx);
          }

          @Override
          public Boolean reduce(Boolean r1, Boolean r2) {
            return Boolean.TRUE.equals(r1) || Boolean.TRUE.equals(r2);
          }
        }.scan(tree, null));
  }

  private ImmutableListMultimap<ClassTree, CodeTransformer> compileMigrationTemplates(
      ClassTree tree) {
    ListMultimap<ClassTree, CodeTransformer> rules = ArrayListMultimap.create();
    new TreeScanner<Void, Context>() {
      @Override
      public Void visitClass(ClassTree node, Context ctx) {
        // Here check for things. Is it our needed transformer?
        rules.putAll(node, RefasterRuleBuilderScanner.extractRules(node, ctx));
        return super.visitClass(node, ctx);
      }
    }.scan(tree, context);
    return ImmutableListMultimap.copyOf(rules);
  }

  private FileObject getOutputFile(TaskEvent taskEvent, ClassTree tree) throws IOException {
    String packageName =
        Optional.ofNullable(ASTHelpers.getSymbol(tree))
            .map(ASTHelpers::enclosingPackage)
            .map(Symbol.PackageSymbol::toString)
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

  private static CharSequence toSimpleFlatName(Symbol.ClassSymbol classSymbol) {
    Name flatName = classSymbol.flatName();
    int lastDot = flatName.lastIndexOf((byte) '.');
    return lastDot < 0 ? flatName : flatName.subSequence(lastDot + 1, flatName.length());
  }

  private static void outputCodeTransformers(List<CodeTransformer> rules, FileObject target)
      throws IOException {
    try (ObjectOutputStream output = new ObjectOutputStream(target.openOutputStream())) {
      output.writeObject(CompositeCodeTransformer.compose(rules));
    }
  }
}
