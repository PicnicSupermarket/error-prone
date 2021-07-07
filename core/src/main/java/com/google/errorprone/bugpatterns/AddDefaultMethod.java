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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.apply.ImportOrganizer.STATIC_FIRST_ORGANIZER;
import static com.sun.tools.javac.code.Flags.DEFAULT;
import static com.sun.tools.javac.tree.JCTree.JCReturn;
import static com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import static com.sun.tools.javac.tree.JCTree.JCBlock;
import static java.util.function.Function.identity;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Streams;
import com.google.errorprone.BugPattern;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import com.google.errorprone.SubContext;
import com.google.errorprone.VisitorState;
import com.google.errorprone.apply.DescriptionBasedDiff;
import com.google.errorprone.apply.SourceFile;
import com.google.errorprone.bugpatterns.BugChecker.CompilationUnitTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import com.google.errorprone.util.ASTHelpers;
import com.google.testing.compile.JavaFileObjects;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.IntHashTable;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Position;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

@BugPattern(
    name = "AddDefaultMethod",
    summary = "First steps in trying to add a default method to an interface",
    severity = ERROR)
public final class AddDefaultMethod extends BugChecker
    implements ClassTreeMatcher, CompilationUnitTreeMatcher {
  private static final String REFASTER_TEMPLATE_SUFFIX = ".refaster";

  private static final Supplier<ImmutableListMultimap<String, CodeTransformer>>
      MIGRATION_TRANSFORMER = Suppliers.memoize(AddDefaultMethod::loadMigrationTransformer);
  private String transformationString;

  private static ImmutableListMultimap<String, CodeTransformer> loadMigrationTransformer() {
    ImmutableListMultimap.Builder<String, CodeTransformer> transformers =
        ImmutableListMultimap.builder();

    String refasterUri =
        "src/main/java/com/google/errorprone/bugpatterns/FirstMigrationTemplate.refaster";
    try (FileInputStream is = new FileInputStream(refasterUri);
        ObjectInputStream ois = new ObjectInputStream(is)) {
      String name = getRefasterTemplateName(refasterUri).orElseThrow(IllegalStateException::new);

      // XXX: Use this instead of the other code.
      ImmutableSetMultimap<Boolean, CodeTransformer> templates =
          unwrap((CodeTransformer) ois.readObject())
              .collect(
                  toImmutableSetMultimap(
                      t -> t.annotations().getInstance(MigrationTemplate.class).value(),
                      identity()));

      transformers.put(name, templates.values().stream().findFirst().get());
    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    }

    return transformers.build();
  }

  private static Stream<CodeTransformer> unwrap(CodeTransformer codeTransformer) {
    if (!(codeTransformer instanceof CompositeCodeTransformer)) {
      return Stream.of(codeTransformer);
    }

    return ((CompositeCodeTransformer) codeTransformer)
        .transformers().stream().flatMap(AddDefaultMethod::unwrap);
  }

  private static Optional<String> getRefasterTemplateName(String resourceName) {
    int lastPathSeparator = resourceName.lastIndexOf('/');
    int beginIndex = lastPathSeparator < 0 ? 0 : lastPathSeparator + 1;
    int endIndex = resourceName.length() - REFASTER_TEMPLATE_SUFFIX.length();
    return Optional.of(resourceName.substring(beginIndex, endIndex));
  }

  @Override
  public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
    ImmutableListMultimap<String, CodeTransformer> migrationTransformationsMap =
        MIGRATION_TRANSFORMER.get();

    TreeMaker treeMaker = state.getTreeMaker();
    JCCompilationUnit compilationUnit = treeMaker.TopLevel(List.nil());
    TreePath compUnitTreePath = new TreePath(compilationUnit);

    JCLiteral literal = treeMaker.Literal("test");
    JCParens parens = treeMaker.Parens(literal);

    TreePath parensPathWithParent = new TreePath(compUnitTreePath, parens);
    TreePath literalPathWithParents = new TreePath(parensPathWithParent, literal);

    SimpleEndPosTable endPosTable = new SimpleEndPosTable();
    String fullSource = parens.getTree().toString();
    endPosTable.storeEnd(parens, fullSource.length());
    endPosTable.storeEnd(
        literal, fullSource.lastIndexOf(literal.toString()) + literal.toString().length());

    JavaFileObject source = JavaFileObjects.forSourceString("XXX", fullSource);
    compilationUnit.sourcefile = source;
    compilationUnit.defs = compilationUnit.defs.append(parens);
    compilationUnit.endPositions = endPosTable;

    CodeTransformer transformer = migrationTransformationsMap.values().stream().findFirst().get();
    Context updatedContext = prepareContext(state.context, compilationUnit);

    java.util.List<Description> matches = new ArrayList<>();
    transformer.apply(literalPathWithParents, updatedContext, matches::add);

    JavaFileObject javaFileObject = null;
    try {
      javaFileObject = applyDiff(source, compilationUnit, matches.get(0));
      transformationString = javaFileObject.getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to apply diff", e);
    }

    return Description.NO_MATCH;
  }

  @Override
  public Description matchClass(ClassTree tree, VisitorState state) {
    TreeMaker treeMaker = state.getTreeMaker();
    Symbol.ClassSymbol interfaceSymbol = ASTHelpers.getSymbol(tree);
    ImmutableList<MethodSymbol> symbolStream =
        getMethodsOfInterfaceToMigrate(
            state.getTypeFromString("java.lang.String"), interfaceSymbol);

    Type newReturnType =
        getMethodTypeWithNewReturnType(state.context, state.getTypeFromString("java.lang.Integer"));

    MethodSymbol sym = symbolStream.get(0);
    sym.flags_field = DEFAULT;
    JCMethodDecl updatedMethodDecl = treeMaker.MethodDef(sym, getBlockWithReturnNull(treeMaker));

    sym.name = sym.name.append(Names.instance(state.context).fromString("_migrated"));
    JCMethodDecl newMethodDecl =
        treeMaker.MethodDef(sym, newReturnType, getBlockWithReturnNull(treeMaker));

    String prevMethodInDefault = updatedMethodDecl.toString();

    // how we know what to match instead of `(\"test\")` in normal cases?
    String fullyMigratedSourceCode =
        newMethodDecl.toString().replace("\"null\"", transformationString);

    if (interfaceSymbol.isInterface()) {
      return buildDescription(tree)
          .addFix(
              SuggestedFix.replace(
                  tree.getMembers().get(0), prevMethodInDefault + fullyMigratedSourceCode))
          .build();
    }
    return Description.NO_MATCH;
  }

  private ImmutableList<MethodSymbol> getMethodsOfInterfaceToMigrate(
      Type migrateFromType, Symbol.ClassSymbol interfaceSymbol) {
    Iterable<Symbol> symbols = interfaceSymbol.members().getSymbols(Scope.LookupKind.NON_RECURSIVE);

    return Streams.stream(symbols)
        .filter(MethodSymbol.class::isInstance)
        .map(MethodSymbol.class::cast)
        .filter(methodSymbol -> methodSymbol.getReturnType() == migrateFromType)
        .collect(toImmutableList());
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
    context.put(Symbol.PackageSymbol.class, compilationUnit.packge);
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
