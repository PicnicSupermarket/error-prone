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
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.apply.ImportOrganizer.STATIC_FIRST_ORGANIZER;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.sun.tools.javac.code.Flags.DEFAULT;
import static com.sun.tools.javac.code.Symbol.ClassSymbol;
import static com.sun.tools.javac.code.Symbol.PackageSymbol;
import static com.sun.tools.javac.code.Type.MethodType;
import static com.sun.tools.javac.tree.JCTree.JCBlock;
import static com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import static com.sun.tools.javac.tree.JCTree.JCExpression;
import static com.sun.tools.javac.tree.JCTree.JCLiteral;
import static com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import static com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import static com.sun.tools.javac.tree.JCTree.JCReturn;
import static com.sun.tools.javac.tree.JCTree.JCVariableDecl;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.SubContext;
import com.google.errorprone.VisitorState;
import com.google.errorprone.apply.DescriptionBasedDiff;
import com.google.errorprone.apply.SourceFile;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.fixes.Fix;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.refaster.CouldNotResolveImportException;
import com.google.errorprone.refaster.Inliner;
import com.google.errorprone.refaster.UType;
import com.google.errorprone.refaster.Unifier;
import com.google.errorprone.util.ASTHelpers;
import com.google.testing.compile.JavaFileObjects;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
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
import com.sun.tools.javac.util.Position;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

@AutoService(BugChecker.class)
@BugPattern(
    name = "AddDefaultMethod", // UndesiredTypeMigrator? MigrateReturnTypes?
    summary = "Rewrite methods with undesired method return types.",
    severity = ERROR)
public class AddDefaultMethod extends BugChecker implements MethodTreeMatcher {
  private static final Supplier<ImmutableList<MigrationCodeTransformer>> MIGRATION_TRANSFORMATIONS =
      MigrationTransformersProvider.MIGRATION_TRANSFORMATIONS;

  // XXX: What would be a better way to provide the imports to the SuggestedFix?
  private Collection<String> importsToAdd;

  @Override
  public Description matchMethod(MethodTree methodTree, VisitorState state) {
    ImmutableList<MigrationCodeTransformer> migrationDefinitions = MIGRATION_TRANSFORMATIONS.get();
    Inliner inliner = new Unifier(state.context).createInliner();

    MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodTree);
    ClassSymbol enclosingClassSymbol = ASTHelpers.enclosingClass(methodSymbol);
    if (enclosingClassSymbol == null) {
      return Description.NO_MATCH;
    }

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

    if (!suitableMigration.isPresent()) {
      return Description.NO_MATCH;
    }

    Type desiredReturnType =
        getDesiredReturnTypeForMigration(state, inliner, methodSymbol, suitableMigration.get());

    if (!enclosingClassSymbol.isInterface()
        && isInterfaceAlreadyMigratedOrNotImplementingOne(
            methodTree, enclosingClassSymbol, state)) {

      boolean isAlreadyMigratedInClass =
          isMethodAlreadyMigratedInEnclosingClass(
              methodTree, state, methodSymbol.getSimpleName(), enclosingClassSymbol);
      boolean annotatedOnlyWithOverrideAndDeprecated =
          methodSymbol.getAnnotationMirrors().stream()
              .map(Object::toString)
              .allMatch(it -> it.contains("Deprecated") || it.contains("Override"));
      if (!enclosingClassSymbol.getInterfaces().isEmpty()
          && isAlreadyMigratedInClass
          && annotatedOnlyWithOverrideAndDeprecated) {
        return describeMatch(methodTree, SuggestedFix.delete(methodTree));
      } else if (isAlreadyMigratedInClass) {
        return Description.NO_MATCH;
      }

      String implExistingMethod =
          getBodyForDefaultMethodInInterface(
              methodSymbol.getSimpleName(),
              inlineType(inliner, suitableMigration.get().typeTo()),
              true,
              suitableMigration.get().transformTo(),
              state,
              methodTree);

      SuggestedFix.Builder fix = SuggestedFix.builder();
      ImmutableList<Description> descriptions =
          ImmutableList.of(
              describeMatch(
                  methodTree,
                  SuggestedFix.prefixWith(
                      methodTree,
                      "@Deprecated"
                          + getCurrentMethodWithUpdatedBody(methodTree, implExistingMethod))),
              describeMatch(
                  methodTree,
                  SuggestedFixes.renameMethod(
                      methodTree, methodTree.getName().toString() + "_migrated", state)),
              getMigrationReplacementForNormalMethod(methodTree, suitableMigration.get(), state),
              getDescriptionToUpdateMethodTreeType(methodTree, desiredReturnType, state));

      descriptions.forEach(d -> fix.merge((SuggestedFix) getOnlyElement(d.fixes)));

      return describeMatch(methodTree, fix.build());
    } else if (enclosingClassSymbol.isInterface()
        && !isMethodAlreadyMigratedInEnclosingClass(
            methodTree, state, methodSymbol.name, enclosingClassSymbol)) {
      String migrationReplacement =
          getMigrationReplacementForMethod(
              methodTree, methodSymbol, desiredReturnType, suitableMigration.get(), inliner, state);

      SuggestedFix.Builder suggestedFix = SuggestedFix.builder();
      suggestedFix.replace(methodTree, migrationReplacement);
      importsToAdd.forEach(imp -> suggestedFix.addImport(imp.replace("import ", "")));
      importsToAdd = new ArrayList<>();

      return describeMatch(methodTree, suggestedFix.build());
    }
    return Description.NO_MATCH;
  }

  private String getCurrentMethodWithUpdatedBody(MethodTree methodTree, String implExistingMethod) {
    return methodTree
        .toString()
        .replace(methodTree.getBody().toString(), "{\n return " + implExistingMethod + "; \n}\n");
  }

  private Type getDesiredReturnTypeForMigration(
      VisitorState state,
      Inliner inliner,
      MethodSymbol methodSymbol,
      MigrationCodeTransformer suitableMigration) {
    Type desiredReturnType = inlineType(inliner, suitableMigration.typeTo());
    if (desiredReturnType != null
        && !desiredReturnType.getTypeArguments().isEmpty()
        && methodSymbol.type
            instanceof MethodType) { // Type$ForAll cannot be  cast to Type$MethodType.
      List<Type> argumentsOfDesiredReturnType =
          ((MethodType) methodSymbol.type).restype.getTypeArguments();
      desiredReturnType =
          state
              .getTypes()
              .subst(
                  desiredReturnType,
                  desiredReturnType.getTypeArguments(),
                  argumentsOfDesiredReturnType);
    }
    return desiredReturnType;
  }

  private boolean isInterfaceAlreadyMigratedOrNotImplementingOne(
      MethodTree methodTree, ClassSymbol enclosingClassSymbol, VisitorState state) {
    List<Type> interfaces = enclosingClassSymbol.getInterfaces();

    return interfaces.isEmpty()
        || interfaces.stream()
            .map(i -> i.tsym)
            .anyMatch(
                a ->
                    a.members()
                        .getSymbolsByName(state.getName(methodTree.getName() + "_migrated"))
                        .iterator()
                        .hasNext());
  }

  private Description getDescriptionToUpdateMethodTreeType(
      MethodTree methodTree, Type newType, VisitorState state) {
    SuggestedFix.Builder builder = SuggestedFix.builder();
    String qualifiedName = SuggestedFixes.qualifyType(state, builder, newType);
    return describeMatch(
        methodTree.getReturnType(),
        builder.replace(methodTree.getReturnType(), qualifiedName).build());
  }

  private static boolean isMethodAlreadyMigratedInEnclosingClass(
      MethodTree methodTree,
      VisitorState state,
      Name methodName,
      ClassSymbol enclosingClassSymbol) {
    return enclosingClassSymbol
            .members()
            .getSymbolsByName(state.getName(methodName + "_migrated"))
            .iterator()
            .hasNext()
        && hasAnnotation(methodTree, Deprecated.class, state);
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
      MethodTree methodTree,
      MigrationCodeTransformer migrationCodeTransformer,
      VisitorState state) {

    JCCompilationUnit compilationUnit = (JCCompilationUnit) state.getPath().getCompilationUnit();
    TreePath compUnitTreePath = new TreePath(compilationUnit);
    TreePath methodPath = new TreePath(compUnitTreePath, methodTree.getBody());

    java.util.List<Description> matches = new ArrayList<>();
    migrationCodeTransformer.transformFrom().apply(methodPath, state.context, matches::add);

    // XXX: This is dangerous, but for now, the first match is always the match of the return type,
    // and we don't need that. Change if that is necessary.
    // The idea behind the last index is that it returns the biggest expression that is matched.
    if (matches.isEmpty()) {
      return Description.NO_MATCH;
    }
    MatchesSolver.applyMatches(matches,
            compilationUnit.endPositions,
            state);
    return matches.get(0);
  }

  private String getBodyForDefaultMethodInInterface(
      Name methodName,
      Type currentType,
      boolean migratingToDesired,
      CodeTransformer transformer,
      VisitorState state,
      MethodTree methodTree) {
    TreeMaker treeMaker = state.getTreeMaker();
    JCCompilationUnit compilationUnit = treeMaker.TopLevel(List.nil());
    TreePath compUnitTreePath = new TreePath(compilationUnit);

    if (migratingToDesired) {
      methodName = state.getName(methodName + "_migrated");
    }

    java.util.List<? extends VariableTree> parameters = methodTree.getParameters();
    List<JCExpression> params =
        parameters.stream()
            .filter(JCVariableDecl.class::isInstance)
            .map(JCVariableDecl.class::cast)
            .map(treeMaker::Ident)
            .collect(List.collector());
    JCExpression identExpr = treeMaker.Ident(methodName).setType(currentType);
    JCMethodInvocation methodInvocation = treeMaker.Apply(List.nil(), identExpr, params);
    methodInvocation.setType(currentType);
    // XXX: here pass typeparams and params... In the List.nil() ^

    TreePath methodInvocationPath = new TreePath(compUnitTreePath, methodInvocation);

    SimpleEndPosTable endPosTable = new SimpleEndPosTable();
    String fullSource = methodInvocation.toString();
    endPosTable.storeEnd(methodInvocation, fullSource.length());
    endPosTable.storeEnd(identExpr, identExpr.toString().length());
    params.forEach(
        p -> endPosTable.storeEnd(p, fullSource.indexOf(p.toString()) + p.toString().length()));

    JavaFileObject source = JavaFileObjects.forSourceString("XXX", fullSource);
    compilationUnit.sourcefile = source;
    compilationUnit.defs = compilationUnit.defs.append(methodInvocation);
    compilationUnit.endPositions = endPosTable;

    Context updatedContext = prepareContext(state.context, compilationUnit);

    java.util.List<Description> matches = new ArrayList<>();
    transformer.apply(methodInvocationPath, updatedContext, matches::add);

    if (matches.isEmpty()) {
      return "";
    }
    JavaFileObject javaFileObject;
    try {
      javaFileObject = applyDiff(source, compilationUnit, matches.get(0));
      return javaFileObject.getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to apply diff", e);
    }
  }

  private String getMigrationReplacementForMethod(
      MethodTree methodTree,
      MethodSymbol methodSymbol,
      Type desiredReturnType,
      MigrationCodeTransformer currentMigration,
      Inliner inliner,
      VisitorState state) {
    TreeMaker treeMaker = state.getTreeMaker();

    String implExistingMethod =
            getBodyForDefaultMethodInInterface(
                    methodSymbol.getSimpleName(),
                    inlineType(inliner, currentMigration.typeTo()),
                    true,
                    currentMigration.transformTo(),
                    state,
                    methodTree);

    // XXX: Also retrieve the imports and add to the builder?
    String implNewMethod =
        getBodyForDefaultMethodInInterface(
            methodSymbol.getSimpleName(),
            methodSymbol.getReturnType(),
            false,
            currentMigration.transformFrom(),
            state,
            methodTree);

    MethodSymbol undesiredDefaultMethodSymbol = methodSymbol.clone(methodSymbol.owner);
    undesiredDefaultMethodSymbol.params = methodSymbol.params;
    undesiredDefaultMethodSymbol.flags_field = DEFAULT;
    JCMethodDecl undesiredDefaultMethodDecl =
        treeMaker.MethodDef(undesiredDefaultMethodSymbol, getBlockWithReturnNull(treeMaker));

    String existingMethodWithDefaultImpl = "  @Deprecated  " + undesiredDefaultMethodDecl;
    existingMethodWithDefaultImpl =
        existingMethodWithDefaultImpl.replace("\"null\"", implExistingMethod);

    Type methodTypeWithReturnType =
        getMethodTypeWithNewReturnType(state.context, desiredReturnType);

    undesiredDefaultMethodSymbol.name =
        undesiredDefaultMethodSymbol.name.append(state.getName("_migrated"));
    JCMethodDecl desiredDefaultMethod =
        treeMaker.MethodDef(
            undesiredDefaultMethodSymbol,
            methodTypeWithReturnType,
            getBlockWithReturnNull(treeMaker));
    java.util.List<? extends VariableTree> parameters = methodTree.getParameters();
    desiredDefaultMethod.params =
        parameters.stream()
            .filter(JCVariableDecl.class::isInstance)
            .map(JCVariableDecl.class::cast)
            .collect(List.collector());

    String implForMigratedMethod =
        desiredDefaultMethod.toString().replace("\"null\"", implNewMethod);

    return existingMethodWithDefaultImpl + implForMigratedMethod;
  }

  private Type getMethodTypeWithNewReturnType(Context context, Type newReturnType) {
    Symtab instance = Symtab.instance(context);
    return new MethodType(List.nil(), newReturnType, List.nil(), instance.methodClass);
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
    Fix fix = description.fixes.get(0);
    SuggestedFix.Builder builder = SuggestedFix.builder();
    if (fix instanceof SuggestedFix) {
      importsToAdd = fix.getImportsToAdd();
      for (String imp : fix.getImportsToAdd()) {
        builder.removeImport(imp.replace("import ", ""));
      }
    }
    diff.handleFix(description.fixes.get(0));
    diff.handleFix(builder.build());

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

  // XXX: Suggestion: (update: suggestion almost fully applied, but leaving it in here to be sure)
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
}
