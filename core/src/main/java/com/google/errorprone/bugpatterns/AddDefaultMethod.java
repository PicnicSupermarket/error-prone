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
import com.google.errorprone.SubContext;
import com.google.errorprone.VisitorState;
import com.google.errorprone.apply.DescriptionBasedDiff;
import com.google.errorprone.apply.SourceFile;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.migration.MigrationCodeTransformer;
import com.google.errorprone.refaster.CouldNotResolveImportException;
import com.google.errorprone.refaster.Inliner;
import com.google.errorprone.refaster.UType;
import com.google.errorprone.refaster.Unifier;
import com.google.errorprone.util.ASTHelpers;
import com.google.testing.compile.JavaFileObjects;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.IntHashTable;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Position;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.apply.ImportOrganizer.STATIC_FIRST_ORGANIZER;
import static com.sun.tools.javac.code.Flags.DEFAULT;
import static com.sun.tools.javac.code.Symbol.ClassSymbol;
import static com.sun.tools.javac.code.Symbol.PackageSymbol;
import static com.sun.tools.javac.tree.JCTree.*;

@BugPattern(
    name = "AddDefaultMethod",
    summary = "First steps in trying to add a default method to an interface",
    severity = ERROR)
public final class AddDefaultMethod extends BugChecker
    implements ClassTreeMatcher, MethodTreeMatcher {
  private static final Supplier<ImmutableList<MigrationCodeTransformer>> MIGRATION_TRANSFORMATIONS =
      Suppliers.memoize(AddDefaultMethod::loadMigrationTransformer);

  private static ImmutableList<MigrationCodeTransformer> loadMigrationTransformer() {
    ImmutableList.Builder<MigrationCodeTransformer> migrationDefinitions =
        new ImmutableList.Builder<>();

    String migrationDefinitionUri =
        "../migration/src/main/java/com/google/errorprone/migration/FirstMigrationTemplate.migration";

    try (FileInputStream is = new FileInputStream(migrationDefinitionUri);
        ObjectInputStream ois = new ObjectInputStream(is)) {

      unwrap((CodeTransformer) ois.readObject())
          .filter(MigrationCodeTransformer.class::isInstance)
          .map(MigrationCodeTransformer.class::cast)
          .forEach(migrationDefinitions::add);
    } catch (IOException | ClassNotFoundException e) {
      // XXX: @Stephan, which exception to throw here?
      throw new IllegalStateException("Failed to read the Refaster migration template", e);
    }

    return migrationDefinitions.build();
  }

  private static Stream<CodeTransformer> unwrap(CodeTransformer codeTransformer) {
    if (!(codeTransformer instanceof CompositeCodeTransformer)) {
      return Stream.of(codeTransformer);
    }

    return ((CompositeCodeTransformer) codeTransformer)
        .transformers().stream().flatMap(AddDefaultMethod::unwrap);
  }

  @Override
  public Description matchMethod(MethodTree methodTree, VisitorState state) {
    ImmutableList<MigrationCodeTransformer> migrationDefinitions = MIGRATION_TRANSFORMATIONS.get();
    Unifier unifier = new Unifier(state.context);
    Inliner inliner = unifier.createInliner();

    MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodTree);
    Type methodReturnType = methodSymbol.getReturnType();

    // XXX: Suggestion:
    // We know that if this return type is eligible for migration, then:
    // 1. There must be a `CodeTransformer` accepting expressions of this type (namely for the
    // migration for the new default method back to the old one).
    // 2. The migration will require us to generate a fake tree of this type.
    // So, given a method which, given a return type and method name produces such a fake tree, we
    // can create such a tree here, and check whether any of the "non-desired" `CodeTransformer`s
    // accepts this tree. We should migrate the method iff so.
    // Downside: this operation is more expensive and linear in the number of types to be migrated.
    // Upside: no `ExpressionTemplate` change required, generalizes to generic types if the rest of
    // the logic supports it too.
    // XXX: Other idea: optimistically attempt to migrate any interface method. Emit fix only if
    // successful. (Upside: avoids non-compiling code in case of config issue or unforeseen
    // limitation.)

    Optional<MigrationCodeTransformer> suitableMigration =
        migrationDefinitions.stream()
            .filter(
                m ->
                    ASTHelpers.isSameType(
                        inlineType(inliner, m.typeFrom()), methodReturnType, state))
            .findFirst();

    ClassSymbol enclosingClassSymbol = ASTHelpers.enclosingClass(methodSymbol);
    if (!suitableMigration.isPresent()
        || enclosingClassSymbol == null
        || !enclosingClassSymbol.isInterface()) {
      return Description.NO_MATCH;
    }

    return describeMatch(
        methodTree,
        SuggestedFix.replace(
            methodTree,
            getMigrationReplacementForMethod(
                methodSymbol, suitableMigration.get(), inliner, state)));
  }

  private Type inlineType(Inliner inliner, UType uType) {
    try {
      return uType.inline(inliner);
    } catch (CouldNotResolveImportException e) {
      throw new IllegalStateException("Couldn't inline UType", e);
    }
  }

  private String getBodyForDefaultMethodInInterface(
      Name methodName,
      Type currentType,
      boolean migratingToDesired,
      CodeTransformer transformer,
      VisitorState state) {
    TreeMaker treeMaker = state.getTreeMaker();
    JCCompilationUnit compilationUnit = treeMaker.TopLevel(List.nil());
    TreePath compUnitTreePath = new TreePath(compilationUnit);

    if (migratingToDesired) {
      methodName = Names.instance(state.context).fromString(methodName + "_migrated");
    }
    JCExpression identExpr = treeMaker.Ident(methodName).setType(currentType);
    JCMethodInvocation methodInvocation = treeMaker.Apply(List.nil(), identExpr, List.nil());
    methodInvocation.setType(currentType);
    // XXX: here pass typeparams and params... In the List.nil() ^

    TreePath methodInvocationPath = new TreePath(compUnitTreePath, methodInvocation);

    SimpleEndPosTable endPosTable = new SimpleEndPosTable();
    String fullSource = methodInvocation.toString();
    endPosTable.storeEnd(methodInvocation, fullSource.length());
    endPosTable.storeEnd(identExpr, identExpr.toString().length());

    JavaFileObject source = JavaFileObjects.forSourceString("XXX", fullSource);
    compilationUnit.sourcefile = source;
    compilationUnit.defs = compilationUnit.defs.append(methodInvocation);
    compilationUnit.endPositions = endPosTable;

    Context updatedContext = prepareContext(state.context, compilationUnit);

    java.util.List<Description> matches = new ArrayList<>();
    transformer.apply(methodInvocationPath, updatedContext, matches::add);

    JavaFileObject javaFileObject;
    try {
      javaFileObject = applyDiff(source, compilationUnit, matches.get(0));
      return javaFileObject.getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to apply diff", e);
    }
  }

  private String getMigrationReplacementForMethod(
      MethodSymbol methodSymbol,
      MigrationCodeTransformer currentMigration,
      Inliner inliner,
      VisitorState state) {
    TreeMaker treeMaker = state.getTreeMaker();

    String implNewMethod =
        getBodyForDefaultMethodInInterface(
            methodSymbol.getSimpleName(),
            methodSymbol.getReturnType(), // ((JCIdent) methodTree.getReturnType()).type,
            false,
            currentMigration.transformFrom(),
            state);

    String implExistingMethod =
        getBodyForDefaultMethodInInterface(
            methodSymbol.getSimpleName(),
            inlineType(inliner, currentMigration.typeTo()),
            true,
            currentMigration.transformTo(),
            state);

    MethodSymbol undesiredDefaultMethodSymbol = methodSymbol.clone(methodSymbol.owner);
    undesiredDefaultMethodSymbol.flags_field = DEFAULT;
    JCMethodDecl undesiredDefaultMethodDecl =
        treeMaker.MethodDef(undesiredDefaultMethodSymbol, getBlockWithReturnNull(treeMaker));

    String existingMethodWithDefaultImpl = undesiredDefaultMethodDecl.toString();
    existingMethodWithDefaultImpl =
        existingMethodWithDefaultImpl.replace("\"null\"", implExistingMethod);

    Type methodTypeWithReturnType =
        getMethodTypeWithNewReturnType(
            state.context, inlineType(inliner, currentMigration.typeTo()));

    undesiredDefaultMethodSymbol.name =
        undesiredDefaultMethodSymbol.name.append(
            Names.instance(state.context).fromString("_migrated"));
    JCMethodDecl desiredDefaultMethod =
        treeMaker.MethodDef(
            undesiredDefaultMethodSymbol,
            methodTypeWithReturnType,
            getBlockWithReturnNull(treeMaker));

    String implForMigratedMethod =
        desiredDefaultMethod.toString().replace("\"null\"", implNewMethod);

    return existingMethodWithDefaultImpl + implForMigratedMethod;
  }

  @Override
  public Description matchClass(ClassTree tree, VisitorState state) {
    // XXX: Here go over the interfaces and try to find methods that should be migrated.
    return Description.NO_MATCH;
  }

  private Type getMethodTypeWithNewReturnType(Context context, Type newReturnType) {
    Symtab instance = Symtab.instance(context);
    return new Type.MethodType(List.nil(), newReturnType, List.nil(), instance.methodClass);
  }

  private JCBlock getBlockWithReturnNull(TreeMaker treeMaker) {
    JCLiteral literal = treeMaker.Literal("null");
    JCReturn aReturn = treeMaker.Return(literal);
    return treeMaker.Block(0, List.of(aReturn));
  }

  private Context prepareContext(Context baseContext, JCCompilationUnit compilationUnit) {
    Context context = new SubContext(baseContext);
    if (context.get(JavaFileManager.class) == null) {
      // XXX: Review whether we can drop this.
      JavacFileManager.preRegister(context);
    }
    context.put(JCCompilationUnit.class, compilationUnit);
    context.put(PackageSymbol.class, compilationUnit.packge);
    return context;
  }

  private JavaFileObject applyDiff(
      JavaFileObject sourceFileObject, JCCompilationUnit compilationUnit, Description description)
      throws IOException {
    DescriptionBasedDiff diff =
        DescriptionBasedDiff.create(compilationUnit, STATIC_FIRST_ORGANIZER);
    // XXX: Make nicer.
    diff.handleFix(description.fixes.get(0));

    SourceFile sourceFile = SourceFile.create(sourceFileObject);
    diff.applyDifferences(sourceFile);

    return JavaFileObjects.forSourceString("XXX", sourceFile.getSourceText());
  }

  static final class SimpleEndPosTable implements EndPosTable {
    private final IntHashTable endPosMap = new IntHashTable();

    @Override
    public void storeEnd(JCTree tree, int endPos) {
      endPosMap.putAtIndex(tree, endPos, endPosMap.lookup(tree));
    }

    @Override
    public int getEndPos(JCTree tree) {
      int value = endPosMap.getFromIndex(endPosMap.lookup(tree));
      return (value == -1) ? Position.NOPOS : value;
    }

    @Override
    public int replaceTree(JCTree oldTree, JCTree newTree) {
      int pos = endPosMap.remove(oldTree);
      if (pos == -1) {
        return Position.NOPOS;
      }

      storeEnd(newTree, pos);
      return pos;
    }
  }
}
