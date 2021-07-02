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
import static java.util.function.Function.identity;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.errorprone.BugPattern;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.CompositeCodeTransformer;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.LiteralTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import com.google.testing.compile.JavaFileObjects;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.TreeMaker;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    name = "AddDefaultMethod",
    summary = "First steps in trying to add a default method in an interface",
    severity = ERROR)
public class AddDefaultMethod extends BugChecker
    implements MethodInvocationTreeMatcher, LiteralTreeMatcher {
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

    //    Context context = new Context();
    //    MaskedClassLoader.preRegisterFileManager(context);
    // Perhaps should be for `createForUtilityPurposes`.
    //    VisitorState state =
    //            VisitorState.createForCustomFindingCollection(
    //                    context,
    //                    description -> {
    //                      String test = description.checkName;
    //                    });
    TreeMaker treeMaker = state.getTreeMaker();
    JCCompilationUnit jcCompilationUnit = treeMaker.TopLevel(com.sun.tools.javac.util.List.nil());
    TreePath treePathTry = new TreePath(jcCompilationUnit);

    JCLiteral test = treeMaker.Literal("test");
    JCParens parens= treeMaker.Parens(test);

    TreePath newTreePath = new TreePath(treePathTry, parens);

    JavaFileObject source = JavaFileObjects.forSourceString("XXX", parens.toString());
    jcCompilationUnit.sourcefile = source;

    CodeTransformer transformer = migrationTransformationsMap.values().stream().findFirst().get();

    List<Description> matches = new ArrayList<>();
    transformer.apply(newTreePath, state.context, matches::add);

    ///// Ignore stuff below this line.

    //    CodeTransformer refasterRule = desiredRules.get(0);
    //    refasterRule.apply();

    //    JCTree.JCLiteral test = treeMaker.Literal("Test");

    CompilationUnitTree compilationUnitOther = state.getPath().getCompilationUnit();
    TreePath path = TreePath.getPath(compilationUnitOther, compilationUnitOther);

    //    new TreePath()
    TreePath second = new TreePath(compilationUnitOther);

    //    TreePath path = JavacTrees.instance().getPath(taskEvent.getTypeElement());
    //    if (path == null) {
    //      path = new TreePath(taskEvent.getCompilationUnit());
    //    }

    // Here try to get a match when you create a TreeLiteral of "2".
    return Description.NO_MATCH;
  }

  @Override
  public Description matchLiteral(LiteralTree tree, VisitorState state) {
    return Description.NO_MATCH;
  }
}
