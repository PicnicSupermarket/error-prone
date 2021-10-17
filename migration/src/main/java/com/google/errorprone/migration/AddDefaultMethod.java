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
import static com.google.errorprone.apply.ImportOrganizer.STATIC_FIRST_ORGANIZER;
import static com.google.errorprone.matchers.Matchers.anyOf;
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
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.refaster.CouldNotResolveImportException;
import com.google.errorprone.refaster.Inliner;
import com.google.errorprone.refaster.UType;
import com.google.errorprone.refaster.Unifier;
import com.google.errorprone.util.ASTHelpers;
import com.google.testing.compile.JavaFileObjects;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.api.JavacTrees;
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
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import org.checkerframework.errorprone.javacutil.TreeUtils;

@AutoService(BugChecker.class)
@BugPattern(
    name = "AddDefaultMethod", // UndesiredTypeMigrator? MigrateReturnTypes?
    summary = "Rewrite methods with undesired method return types.",
    severity = ERROR)
public class AddDefaultMethod extends BugChecker implements MethodTreeMatcher {
  private static final Supplier<ImmutableList<MigrationCodeTransformer>> MIGRATION_TRANSFORMATIONS =
      MigrationTransformersProvider.MIGRATION_TRANSFORMATIONS;

  public static final Matcher<Tree> HAS_REFASTER_ANNOTATION =
      anyOf(
          Matchers.hasAnnotation("com.google.errorprone.refaster.annotation.AfterTemplate"),
          Matchers.hasAnnotation("com.google.errorprone.refaster.annotation.BeforeTemplate"),
          Matchers.hasAnnotation("com.google.errorprone.refaster.annotation.Placeholder"));

  // XXX: What would be a better way to provide the imports to the SuggestedFix?
  private Collection<String> importsToAdd;

  @Override
  public Description matchMethod(MethodTree methodTree, VisitorState state) {
    ImmutableList<MigrationCodeTransformer> migrationDefinitions = MIGRATION_TRANSFORMATIONS.get();
    Inliner inliner = new Unifier(state.context).createInliner();

    MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodTree);
    ClassSymbol enclosingClassSymbol = ASTHelpers.enclosingClass(methodSymbol);
    if (enclosingClassSymbol == null
        || hasAnnotation(enclosingClassSymbol, FunctionalInterface.class.getName(), state)
        || HAS_REFASTER_ANNOTATION.matches(methodTree, state)) {
      return Description.NO_MATCH;
    }

    // Check this one out
    DocCommentTree docCommentTree =
        JavacTrees.instance(state.context).getDocCommentTree(state.getPath());

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
        getDesiredReturnTypeForMigration(methodSymbol, suitableMigration.get(), inliner, state);

    if (!enclosingClassSymbol.isInterface()
        && isInterfaceAlreadyMigratedOrNotImplementingOne(
            methodTree, enclosingClassSymbol, state)) {

      boolean isAlreadyMigratedInClass =
          isMethodAlreadyMigratedInEnclosingClass(
              methodTree, enclosingClassSymbol, methodSymbol.getSimpleName(), state);
      boolean annotatedOnlyWithOverrideAndDeprecated =
          methodSymbol.getAnnotationMirrors().stream()
              .map(Object::toString)
              .allMatch(it -> it.contains("Deprecated") || it.contains("Override"));
      if (!enclosingClassSymbol.getInterfaces().isEmpty()
          && isAlreadyMigratedInClass
          && annotatedOnlyWithOverrideAndDeprecated) {
        // Here we did delete methods, if it was safe to do so. The Unused bugpattern now does this.
        return Description.NO_MATCH;
      } else if (isAlreadyMigratedInClass
          || TreeUtils.elementFromDeclaration(methodTree)
              .getModifiers()
              .contains(Modifier.ABSTRACT)) {
        return Description.NO_MATCH;
      }

      String delegatingMethodBody =
          getBodyForMethod(
              methodTree,
              methodSymbol.getSimpleName(),
              inlineType(inliner, suitableMigration.get().typeTo()),
              true,
              suitableMigration.get().transformTo(),
              state);

      SuggestedFix fix =
          Stream.of(
                  SuggestedFix.prefixWith(
                      methodTree,
                      "@Deprecated\n"
                          + getOriginalMethodWithDelegatingBody(
                              methodTree, delegatingMethodBody, state)),
                  SuggestedFixes.renameMethod(
                      methodTree, methodTree.getName() + "_migrated", state),
                  getDescriptionToUpdateMethodTreeType(methodTree, desiredReturnType, state),
                  getBodyOfMigratedMethod(methodTree, suitableMigration.get(), state))
              .reduce(
                  SuggestedFix.builder(), SuggestedFix.Builder::merge, SuggestedFix.Builder::merge)
              .build();

      return describeMatch(methodTree, fix);
    } else if (enclosingClassSymbol.isInterface()
        && !isMethodAlreadyMigratedInEnclosingClass(
            methodTree, enclosingClassSymbol, methodSymbol.name, state)) {

      SuggestedFix.Builder suggestedFix;

      SuggestedFix bodyOfMigratedMethod =
          getBodyOfMigratedMethod(methodTree, suitableMigration.get(), state);

      String fullMigrationReplacementForInterface =
          getMigrationReplacementForMethodsOfInterface(
              methodTree, methodSymbol, desiredReturnType, suitableMigration.get(), inliner, state);
      if (methodTree.getBody() != null) {
        suggestedFix =
            Stream.of(
                    SuggestedFix.prefixWith(methodTree, fullMigrationReplacementForInterface),
                    SuggestedFixes.renameMethod(
                        methodTree, methodTree.getName() + "_migrated", state),
                    getDescriptionToUpdateMethodTreeType(methodTree, desiredReturnType, state),
                    bodyOfMigratedMethod)
                .reduce(
                    SuggestedFix.builder(),
                    SuggestedFix.Builder::merge,
                    SuggestedFix.Builder::merge);
      } else {
        suggestedFix =
            SuggestedFix.builder().replace(methodTree, fullMigrationReplacementForInterface);
      }
      importsToAdd.forEach(imp -> suggestedFix.addImport(imp.replace("import ", "")));
      importsToAdd = new ArrayList<>();

      return describeMatch(methodTree, suggestedFix.build());
    }
    return Description.NO_MATCH;
  }

  private String getOriginalMethodWithDelegatingBody(
      MethodTree methodTree, String delegatingMethodBody, VisitorState state) {
    String sourceForNode = state.getSourceForNode(methodTree.getBody());
    String sourceForMethod = state.getSourceForNode(methodTree);
    return sourceForMethod.replace(sourceForNode, "{\n return " + delegatingMethodBody + ";\n}\n");
  }

  private Type getDesiredReturnTypeForMigration(
      MethodSymbol methodSymbol,
      MigrationCodeTransformer suitableMigration,
      Inliner inliner,
      VisitorState state) {
    Type desiredReturnType = inlineType(inliner, suitableMigration.typeTo());
    if (desiredReturnType.getTypeArguments() == null) {
      return desiredReturnType;
    }

    List<Type> typeArguments = null;
    if (!desiredReturnType.getTypeArguments().isEmpty()
        && methodSymbol.type instanceof MethodType) {
      typeArguments = ((MethodType) methodSymbol.type).restype.getTypeArguments();
    } else if (methodSymbol.type instanceof Type.ForAll) {
      typeArguments =
          ((MethodType) ((Type.ForAll) methodSymbol.type).qtype).restype.getTypeArguments();
    }

    if (typeArguments != null) {
      return state
          .getTypes()
          .subst(desiredReturnType, desiredReturnType.getTypeArguments(), typeArguments);
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
                        .hasNext())
        || interfaces.stream()
            .map(i -> i.tsym)
            .noneMatch(
                a ->
                    a.members()
                        .getSymbolsByName(state.getName(methodTree.getName().toString()))
                        .iterator()
                        .hasNext());
  }

  private SuggestedFix getDescriptionToUpdateMethodTreeType(
      MethodTree methodTree, Type newType, VisitorState state) {
    SuggestedFix.Builder builder = SuggestedFix.builder();
    String qualifiedName = SuggestedFixes.qualifyType(state, builder, newType);
    return builder.replace(methodTree.getReturnType(), qualifiedName).build();
  }

  private static boolean isMethodAlreadyMigratedInEnclosingClass(
      MethodTree methodTree,
      ClassSymbol enclosingClassSymbol,
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
    // XXX: Explain new `Inliner` creation.
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

  /**
   * The method body is scanned for `ReturnTree`s. These expressions are migrated where necessary.
   */
  private SuggestedFix getBodyOfMigratedMethod(
      MethodTree methodTree,
      MigrationCodeTransformer migrationCodeTransformer,
      VisitorState state) {

    JCCompilationUnit compilationUnit = (JCCompilationUnit) state.getPath().getCompilationUnit();
    TreePath compUnitTreePath = new TreePath(compilationUnit);

    ReturnTreeScanner returnTypeScanner = new ReturnTreeScanner();
    returnTypeScanner.scan(methodTree.getBody(), null);
    List<ReturnTree> returnTrees = returnTypeScanner.getReturnTrees();

    java.util.List<Description> matches = new ArrayList<>();
    returnTrees.forEach(
        e ->
            migrationCodeTransformer
                .transformFrom()
                .apply(new TreePath(compUnitTreePath, e), state.context, matches::add));

    return MatchesSolver.collectNonOverlappingFixes(matches, compilationUnit.endPositions);
  }

  private String getBodyForMethod(
      MethodTree methodTree,
      Name methodName,
      Type currentType,
      boolean migratingToDesired,
      CodeTransformer transformer,
      VisitorState state) {
    TreeMaker treeMaker = state.getTreeMaker();
    treeMaker = treeMaker.at(0);

    JCCompilationUnit compilationUnit = treeMaker.TopLevel(List.nil());
    TreePath compUnitTreePath = new TreePath(compilationUnit);

    methodName = state.getNames().fromString(methodName.toString());
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
    treeMaker = treeMaker.at(0);
    JCExpression identExpr = treeMaker.Ident(methodName).setType(currentType);
    treeMaker = treeMaker.at(0);
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

  private String getMigrationReplacementForMethodsOfInterface(
      MethodTree methodTree,
      MethodSymbol methodSymbol,
      Type desiredReturnType,
      MigrationCodeTransformer currentMigration,
      Inliner inliner,
      VisitorState state) {
    TreeMaker treeMaker = state.getTreeMaker();

    String originalMethodWithDelegationToMigration =
        getBodyForMethod(
            methodTree,
            methodSymbol.getSimpleName(),
            inlineType(inliner, currentMigration.typeTo()),
            true,
            currentMigration.transformTo(),
            state);

    // XXX: Also retrieve the imports and add to the builder?
    String migratedMethodImplementation =
        getBodyForMethod(
            methodTree,
            methodSymbol.getSimpleName(),
            methodSymbol.getReturnType(),
            false,
            currentMigration.transformFrom(),
            state);

    MethodSymbol undesiredDefaultMethodSymbol = methodSymbol.clone(methodSymbol.owner);
    undesiredDefaultMethodSymbol.params = methodSymbol.params;
    undesiredDefaultMethodSymbol.flags_field = DEFAULT;
    JCMethodDecl undesiredDefaultMethodDecl =
        treeMaker.MethodDef(undesiredDefaultMethodSymbol, getBlockWithReturnNull(treeMaker));

    String originalMethodWithDefaultImpl = "  @Deprecated  " + undesiredDefaultMethodDecl;
    originalMethodWithDefaultImpl =
        originalMethodWithDefaultImpl.replace("\"null\"", originalMethodWithDelegationToMigration);

    Type methodTypeWithReturnType =
        getMethodTypeWithNewReturnType(desiredReturnType, state.context);

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
        desiredDefaultMethod.toString().replace("\"null\"", migratedMethodImplementation);

    String result = originalMethodWithDefaultImpl;
    if (methodTree.getBody() == null) {
      result += implForMigratedMethod;
    }

    return result;
  }

  private Type getMethodTypeWithNewReturnType(Type newReturnType, Context context) {
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

  private static final class ReturnTreeScanner extends TreeScanner<Void, Void> {
    private List<ReturnTree> returnTrees = List.nil();

    public List<ReturnTree> getReturnTrees() {
      return returnTrees;
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
      returnTrees = returnTrees.append(tree);
      return super.visitReturn(tree, unused);
    }
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
