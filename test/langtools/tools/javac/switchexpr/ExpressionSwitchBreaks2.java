/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @compile/fail/ref=ExpressionSwitchBreaks2.out -XDrawDiagnostics ExpressionSwitchBreaks2.java
 */

public class ExpressionSwitchBreaks2 {
    private String print(int i, int j) {
        LOOP: while (true) {
        OUTER: switch (i) {
            case 0:
                return switch (j) {
                    case 0:
                        break "0-0";
                    case 1:
                        break ; //error: missing value
                    case 2:
                        break OUTER; //error: jumping outside of the switch expression
                    case 3: {
                        int x = -1;
                        x: switch (i + j) {
                            case 0: break x; //error: cannot disambiguate, wrong type as well
                        }
                        break "X";
                    }
                    case 4: return "X"; //error: no returns from inside of the switch expression
                    case 5: continue;   //error: no continue out of the switch expression
                    case 6: continue LOOP; //error: dtto, but with a label
                    case 7: continue UNKNOWN; //error: unknown label
                    default: {
                        String x = "X";
                        x: switch (i + j) {
                            case 0: break x; //error: cannot disambiguate
                        }
                        break "X";
                    }
                };
            case 1:
                break "1" + undef; //error: complex value and no switch expression
        }
        }
        j: print(switch (i) {
            default: break j;
        }, 0);
        j2: print(switch (i) {
            default: break j2;
        }, 0);
        return null;
    }

}
