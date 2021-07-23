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

import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.sun.tools.javac.code.Symbol.*;
import static com.sun.tools.javac.tree.JCTree.*;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.VisitorState;
import com.google.errorprone.refaster.RefasterRuleBuilderScanner;
import com.google.errorprone.refaster.UTemplater;
import com.google.errorprone.refaster.UType;
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
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.type.TypeKind;
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
        ImmutableList<ClassTree> classTreeWithFullMigrationDefinition =
            node.getMembers().stream()
                .filter(ClassTree.class::isInstance)
                .map(ClassTree.class::cast)
                .collect(toImmutableList());

        if (classTreeWithFullMigrationDefinition.size() != 2) {
          return super.visitClass(node, ctx);
        }

        ImmutableList<? extends Tree> fromMigrationDefinition =
            getMethodsOfMigrationDefinition(classTreeWithFullMigrationDefinition.get(0));
        ImmutableList<? extends Tree> toMigrationDefinition =
            getMethodsOfMigrationDefinition(classTreeWithFullMigrationDefinition.get(1));

        if (fromMigrationDefinition.size() != 2 || toMigrationDefinition.size() != 2) {
          return super.visitClass(node, ctx);
        }

        VisitorState state = VisitorState.createForUtilityPurposes(ctx);
        ImmutableList<MethodSymbol> fromMigrationMethods =
            getMethodSymbolsWithRefasterAnnotation(fromMigrationDefinition, state);
        ImmutableList<MethodSymbol> toMigrationMethods =
            getMethodSymbolsWithRefasterAnnotation(toMigrationDefinition, state);

        if (fromMigrationMethods.size() != 2
            || toMigrationMethods.size() != 2
            || !validateMigrationDefinitions(fromMigrationMethods, toMigrationMethods)) {
          return super.visitClass(node, ctx);
        }

        Type fromBeforeTemplateReturnType =
            ASTHelpers.getType(classTreeWithFullMigrationDefinition.get(0).getMembers().get(1))
                .getReturnType();
        Type fromAfterTemplateReturnType =
            ASTHelpers.getType(classTreeWithFullMigrationDefinition.get(0).getMembers().get(2))
                .getReturnType();

        UTemplater templater = new UTemplater(new HashMap<>(), context);
        UType fromUType = templater.template(fromBeforeTemplateReturnType);
        UType toUType = templater.template(fromAfterTemplateReturnType);

        CodeTransformer migrationFrom =
            RefasterRuleBuilderScanner.extractRules(
                    classTreeWithFullMigrationDefinition.get(0), ctx)
                .iterator()
                .next();

        CodeTransformer migrationTo =
            RefasterRuleBuilderScanner.extractRules(
                    classTreeWithFullMigrationDefinition.get(1), ctx)
                .iterator()
                .next();

        MigrationCodeTransformer migrationCodeTransformer =
            MigrationCodeTransformer.create(migrationFrom, migrationTo, fromUType, toUType);
        rules.put(node, migrationCodeTransformer);
        return super.visitClass(node, ctx);
      }
    }.scan(tree, context);
    return ImmutableMap.copyOf(rules);
  }

  private ImmutableList<MethodSymbol> getMethodSymbolsWithRefasterAnnotation(
      ImmutableList<? extends Tree> beforeDefinition, VisitorState state) {
    return beforeDefinition.stream()
        .map(ASTHelpers::getSymbol)
        .filter(MethodSymbol.class::isInstance)
        .map(MethodSymbol.class::cast)
        .filter(
            sym ->
                ASTHelpers.hasAnnotation(sym, BeforeTemplate.class, state)
                    || ASTHelpers.hasAnnotation(sym, AfterTemplate.class, state))
        .collect(toImmutableList());
  }

  private ImmutableList<? extends Tree> getMethodsOfMigrationDefinition(
      ClassTree classMigrationDefinition) {
    return classMigrationDefinition.getMembers().stream()
        .filter(JCMethodDecl.class::isInstance)
        .filter(e -> !((JCMethodDecl) e).name.contentEquals("<init>"))
        .collect(toImmutableList());
  }

  // XXX: Validation can be improved by giving a ClassTree (which represents the migration
  // definition) to a BugPattern. Everything can be visited and the correct checks can be handled
  // inside the BugPattern. Here we can then check whether the List<Description> is empty.
  // The advantage is that the BugPattern can find all the bugs and provide a list of all the things
  // that need to improve. In comparison to this validation that stops after it finds the first
  // error.
  private boolean validateMigrationDefinitions(
      ImmutableList<MethodSymbol> fromMigrationDefinition,
      ImmutableList<MethodSymbol> toMigrationDefinition) {
    Types types = Types.instance(context);

    validateParamsOfTemplate(fromMigrationDefinition, types);
    validateParamsOfTemplate(toMigrationDefinition, types);

    validateReturnTypeMatchesParamType(fromMigrationDefinition.get(0), types);
    validateReturnTypeMatchesParamType(toMigrationDefinition.get(0), types);

    MethodSymbol fromAfterTemplateMethodSymbol = fromMigrationDefinition.get(1);
    MethodSymbol toAfterTemplateMethodSymbol = toMigrationDefinition.get(1);
    validateParamTypeWithReturnType(
        types, toAfterTemplateMethodSymbol, fromAfterTemplateMethodSymbol);
    validateParamTypeWithReturnType(
        types, fromAfterTemplateMethodSymbol, toAfterTemplateMethodSymbol);

    return true;
  }

  private void validateParamTypeWithReturnType(
      Types types, MethodSymbol paramMethodSymbol, MethodSymbol returnMethodSymbol) {
    Type type = paramMethodSymbol.getParameters().get(0).type;
    boolean isTypeVar = type.getKind() == TypeKind.TYPEVAR;

    Type returnType = returnMethodSymbol.getReturnType();
    // XXX: Make sure that type vars are also checked for the bounds.
    // XXX: Would this be too strict?
    //    type.toString().equals(returnType.toString())
    if (isTypeVar) {
      VisitorState state = VisitorState.createForUtilityPurposes(context);
      checkState(
          ASTHelpers.isSameType(type.getUpperBound(), (returnType.getUpperBound()), state)
              || ASTHelpers.isSameType(type.getLowerBound(), (returnType.getLowerBound()), state),
          "%s parameterType doesn't match %s returnType of the other definition, this concerns a TypeKind.TYPEVAR.",
          paramMethodSymbol.toString(),
          returnMethodSymbol.toString());
    } else {
      checkState(
          types.isSameType(
              paramMethodSymbol.getParameters().get(0).type, returnMethodSymbol.getReturnType()),
          "%s parameterType doesn't match %s returnType of the other definition",
          paramMethodSymbol.toString(),
          returnMethodSymbol.toString());
    }
  }

  private void validateParamsOfTemplate(
      ImmutableList<MethodSymbol> fromMigrationDefinition, Types types) {
    List<VarSymbol> beforeTemplateParameters = fromMigrationDefinition.get(0).getParameters();
    checkState(
        beforeTemplateParameters.size() == 1,
        "BeforeTemplate of MigrationTemplate can contain only 1 parameter, see %s.",
        fromMigrationDefinition.toString());
    List<VarSymbol> afterTemplateParameters = fromMigrationDefinition.get(1).getParameters();
    checkState(
        afterTemplateParameters.size() == 1,
        "AfterTemplate of MigrationTemplate can contain only 1 parameter, see %s.",
        fromMigrationDefinition.toString());
    checkState(
        types.isSameType(beforeTemplateParameters.get(0).type, afterTemplateParameters.get(0).type),
        "Parameter Type of BeforeTemplate should match the Type of the AfterTemplate. See `%s` and `%s`",
        fromMigrationDefinition.get(0),
        fromMigrationDefinition.get(1));
  }

  private void validateReturnTypeMatchesParamType(
      MethodSymbol migrationDefinitionBeforeTemplate, Types types) {
    Type fromReturnType = migrationDefinitionBeforeTemplate.getReturnType();
    List<VarSymbol> fromParameterTypes = migrationDefinitionBeforeTemplate.getParameters();
    Type paramType = fromParameterTypes.get(0).type;

    // XXX: Stephan, you mentioned the AutoValue#equals, but I think this is what is appropriate in
    // this case.
    Preconditions.checkState(
        types.isSameType(fromReturnType, paramType),
        "@BeforeTemplate %s - ReturnType does not match parameter type.",
        migrationDefinitionBeforeTemplate.toString());
  }

  // method to see whether to given methods, are a mapping from on to the other.

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
// look here at the $, is that OK ? Why are all the bytes read from the inputstream empty otherwise?
    JavaFileManager fileManager = context.get(JavaFileManager.class);
    return fileManager.getFileForOutput(
        StandardLocation.CLASS_OUTPUT, packageName, relativeName, taskEvent.getSourceFile());
  }

  private static CharSequence toSimpleFlatName(ClassSymbol classSymbol) {
    Name flatName = classSymbol.getQualifiedName();
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
