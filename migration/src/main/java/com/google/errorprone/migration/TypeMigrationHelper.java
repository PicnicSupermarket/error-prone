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

import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import javax.lang.model.type.TypeKind;

public class TypeMigrationHelper {

    protected static boolean isMethodTypeUndesiredMigrationType(
            Types types, Type methodReturnType, Type undesiredMigrationReturnType, VisitorState state) {
        if (undesiredMigrationReturnType == null) {
            return false;
        }
        if (undesiredMigrationReturnType.getTypeArguments().isEmpty()) {
            return types.isSameType(methodReturnType, undesiredMigrationReturnType);
        }

        Type undesiredReturnTypeArgument = undesiredMigrationReturnType.getTypeArguments().get(0);
        boolean isTypeVar = undesiredReturnTypeArgument.getKind() == TypeKind.TYPEVAR;

        if (isTypeVar) {
            return ASTHelpers.isSameType(methodReturnType, undesiredMigrationReturnType, state)
                    && ASTHelpers.isSubtype(
                    methodReturnType.getTypeArguments().get(0),
                    undesiredReturnTypeArgument.getUpperBound(),
                    state)
                    && ASTHelpers.isSubtype(
                    undesiredReturnTypeArgument.getLowerBound(),
                    methodReturnType.getTypeArguments().get(0),
                    state);
        } else {
            return types.isSameType(methodReturnType, undesiredMigrationReturnType)
                    && (methodReturnType.tsym.equals(undesiredMigrationReturnType.tsym)
                    && methodReturnType
                    .tsym
                    .getTypeParameters()
                    .equals(undesiredMigrationReturnType.tsym.getTypeParameters()));
        }
    }

}
