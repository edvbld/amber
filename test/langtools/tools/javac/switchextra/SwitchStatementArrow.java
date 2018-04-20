/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @compile SwitchStatementArrow.java
 * @run main SwitchStatementArrow
 */
// * @compile/fail/ref=MultipleLabelsStatement-old.out -source 9 -Xlint:-options -XDrawDiagnostics MultipleLabelsStatement.java

import java.util.Objects;
import java.util.function.Function;

public class SwitchStatementArrow {
    public static void main(String... args) {
        new SwitchStatementArrow().run();
    }

    private void run() {
        runTest(this::statement1);
    }

    private void runTest(Function<T, String> print) {
        check(null, print, "NULL-A");
        check(T.A,  print, "NULL-A");
        check(T.B,  print, "B-C");
        check(T.C,  print, "B-C");
        try {
            print.apply(T.D);
            throw new AssertionError();
        } catch (IllegalStateException ex) {
            if (!Objects.equals("D", ex.getMessage()))
                throw new AssertionError(ex);
        }
        check(T.E,  print, "other");
    }

    private String statement1(T t) {
        String res;

        switch (t) {
            case null, A -> { res = "NULL-A"; }
            case B, C -> res = "B-C";
            case D -> throw new IllegalStateException("D");
            default -> { res = "other"; break; }
        }

        return res;
    }

    private void check(T t, Function<T, String> print, String expected) {
        String result = print.apply(t);
        if (!Objects.equals(result, expected)) {
            throw new AssertionError("Unexpected result: " + result);
        }
    }

    enum T {
        A, B, C, D, E;
    }
}
