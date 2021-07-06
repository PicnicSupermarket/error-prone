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

import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.sun.tools.javac.tree.JCTree.*;
import static java.util.function.Function.identity;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.errorprone.BugPattern;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import com.google.errorprone.ErrorProneOptions;
import com.google.errorprone.ImportOrderParser;
import com.google.errorprone.SubContext;
import com.google.errorprone.VisitorState;
import com.google.errorprone.apply.DescriptionBasedDiff;
import com.google.errorprone.apply.ImportOrganizer;
import com.google.errorprone.apply.SourceFile;
import com.google.errorprone.bugpatterns.BugChecker.LiteralTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.CompilationUnitTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import com.google.errorprone.scanner.ErrorProneScannerTransformer;
import com.google.errorprone.scanner.ScannerSupplier;
import com.google.testing.compile.JavaFileObjects;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.IntHashTable;
import com.sun.tools.javac.util.Position;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

@BugPattern(
    name = "AddDefaultMethod",
    summary = "First steps in trying to add a default method to an interface",
    severity = ERROR)
public class AddDefaultMethod extends BugChecker
        implements MethodInvocationTreeMatcher, LiteralTreeMatcher, CompilationUnitTreeMatcher {
  private static final String REFASTER_TEMPLATE_SUFFIX = ".refaster";

private static final Supplier<ImmutableListMultimap<String, CodeTransformer>>
      MIGRATION_TRANSFORMER = Suppliers.memoize(AddDefaultMethod::loadMigrationTransformer);

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
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    ImmutableListMultimap<String, CodeTransformer> migrationTransformationsMap =
        MIGRATION_TRANSFORMER.get();

    TreeMaker treeMaker = state.getTreeMaker();
    JCCompilationUnit jcCompilationUnit = treeMaker.TopLevel(com.sun.tools.javac.util.List.nil());
    TreePath compUnitTreePath = new TreePath(jcCompilationUnit);

    JCLiteral literal = treeMaker.Literal("test");
    JCParens parens = treeMaker.Parens(literal);
    JCReturn aReturn = treeMaker.Return(parens);

    TreePath returnPathWithExpr = new TreePath(compUnitTreePath, aReturn);
    TreePath parensPathWithParent = new TreePath(returnPathWithExpr, parens);
    TreePath literalPathWithParents = new TreePath(parensPathWithParent, literal);

    SimpleEndPosTable endPosTable = new SimpleEndPosTable(null);
    endPosTable.storeEnd(aReturn, aReturn.expr.toString().length());
    endPosTable.storeEnd(literal, aReturn.getTree().toString().lastIndexOf(literal.toString()) + literal.toString().length());

                                                                                  // literalPathWithParents.getCompilationUnit().toString()  -
    JavaFileObject source = JavaFileObjects.forSourceString("XXX", returnPathWithExpr.getLeaf().toString());
    jcCompilationUnit.sourcefile = source;
    jcCompilationUnit.defs = jcCompilationUnit.defs.append(aReturn);
    jcCompilationUnit.endPositions = endPosTable;

    CodeTransformer transformer = migrationTransformationsMap.values().stream().findFirst().get();
    Context updatedContext = prepareContext(state.context, (JCCompilationUnit) literalPathWithParents.getCompilationUnit());

    JCCompilationUnit literalCompUnit = (JCCompilationUnit) literalPathWithParents.getCompilationUnit();
    literalCompUnit.sourcefile = source;
    literalCompUnit.defs = literalCompUnit.defs.append(literal);
    literalCompUnit.endPositions = endPosTable;

    List<Description> matches = new ArrayList<>();
    transformer.apply(literalPathWithParents, updatedContext, matches::add);

    JavaFileObject javaFileObject = null;
    try {
      javaFileObject = applyDiff(source, jcCompilationUnit, matches.get(0));
    } catch (IOException e) {
      e.printStackTrace();
    }

    return Description.NO_MATCH;
  }

  private JavaFileObject applyDiff(
          JavaFileObject sourceFileObject, JCCompilationUnit tree, Description description) throws IOException {

    ImportOrganizer importOrganizer = ImportOrderParser.getImportOrganizer("static-first");
    final DescriptionBasedDiff diff = DescriptionBasedDiff.create(tree, importOrganizer);
    diff.handleFix(description.fixes.get(0));

    SourceFile sourceFile = SourceFile.create(sourceFileObject);
    diff.applyDifferences(sourceFile);

    JavaFileObject transformed =
            JavaFileObjects.forSourceString("XXX", sourceFile.getSourceText());
    return transformed;
  }

  private Context prepareContext(Context baseContext, JCCompilationUnit compilationUnit) {
    Context context = new SubContext(baseContext);
    if (context.get(JavaFileManager.class) == null) {
      JavacFileManager.preRegister(context);
    }
    context.put(JCCompilationUnit.class, compilationUnit);
    context.put(Symbol.PackageSymbol.class, compilationUnit.packge);
    return context;
  }

  @Override
  public Description matchLiteral(LiteralTree tree, VisitorState state) {
    return Description.NO_MATCH;
  }

  @Override
  public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
    return Description.NO_MATCH;
  }

  protected static class SimpleEndPosTable extends AbstractEndPosTable {

    private final IntHashTable endPosMap;

    SimpleEndPosTable(JavacParser parser) {
      super(parser);
      endPosMap = new IntHashTable();
    }

    public void storeEnd(JCTree tree, int endpos) {
      endPosMap.putAtIndex(tree, errorEndPos > endpos ? errorEndPos : endpos,
              endPosMap.lookup(tree));
    }

    protected <T extends JCTree> T to(T t) {
      storeEnd(t, parser.token().endPos);
      return t;
    }

    public int getEndPos(JCTree tree) {
      int value = endPosMap.getFromIndex(endPosMap.lookup(tree));
      // As long as Position.NOPOS==-1, this just returns value.
      return (value == -1) ? Position.NOPOS : value;
    }

    public int replaceTree(JCTree oldTree, JCTree newTree) {
      int pos = endPosMap.remove(oldTree);
      if (pos != -1) {
        storeEnd(newTree, pos);
        return pos;
      }
      return Position.NOPOS;
    }
  }

  protected abstract static class AbstractEndPosTable implements EndPosTable {
    protected JavacParser parser;
    public int errorEndPos = -1;

    public AbstractEndPosTable(JavacParser parser) {
      this.parser = parser;
    }

    protected abstract <T extends JCTree> T to(T var1);

  }
}
