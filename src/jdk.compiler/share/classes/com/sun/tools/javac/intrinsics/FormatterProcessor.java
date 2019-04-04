/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.javac.intrinsics;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Formatter;
import java.util.Locale;

import static java.lang.constant.ConstantDescs.CD_CallSite;
import static java.lang.constant.ConstantDescs.CD_String;

/**
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class FormatterProcessor implements IntrinsicProcessor {
    @Override
    public void register(Intrinsics intrinsics) {
        this.intrinsics = intrinsics;
        intrinsics.register(this,
                String.class, "format", String.class, String.class, Object[].class);
        intrinsics.register(this,
                String.class, "format", String.class, Locale.class, String.class, Object[].class);
        intrinsics.register(this,
                String.class, "format", String.class, Object[].class);
        intrinsics.register(this,
                String.class, "format", String.class, Locale.class, Object[].class);
        intrinsics.register(this,
                Formatter.class, "format", Formatter.class, String.class, Object[].class);
        intrinsics.register(this,
                Formatter.class, "format", Formatter.class, Locale.class, String.class, Object[].class);
    }

    Intrinsics intrinsics;

    static String lowerFirst(String string) {
        return string.substring(0, 1).toLowerCase() + string.substring(1);
    }

    static String upperFirst(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    private String getBSMName(ClassDesc ownerDesc, String methodName, boolean isStatic, boolean hasLocale) {
        StringBuffer sb = new StringBuffer(32);
        if (isStatic) {
            sb.append("static");
            sb.append(ownerDesc.displayName());
        } else {
            sb.append(lowerFirst(ownerDesc.displayName()));
        }

        if (hasLocale) {
            sb.append("Locale");
        }

        sb.append(upperFirst(methodName));
        sb.append("Bootstrap");
        return sb.toString();
    }

    private static final ClassDesc CD_Locale = ClassDesc.of("java.util.Locale");
    private static final ClassDesc CD_IntrinsicFactory = ClassDesc.of("java.lang.invoke.IntrinsicFactory");

    @Override
    public Result tryIntrinsify(ClassDesc ownerDesc,
                                String methodName,
                                MethodTypeDesc methodType,
                                boolean isStatic,
                                ClassDesc[] argClassDescs,
                                ConstantDesc[] constantArgs) {
        if (intrinsics.isArrayVarArg(argClassDescs, methodType.parameterCount())) {
            return new Result.None();
        }

        boolean hasLocale = CD_Locale.equals(methodType.parameterType(0));
        int formatArg = hasLocale ? 2 : 1;

        if (CD_String.equals(ownerDesc)) {
            formatArg = isStatic && hasLocale ? 1 : 0;
        }

        ConstantDesc constantFormat = constantArgs[formatArg];

        if (constantFormat == null) {
            return new Result.None();
        }

        if (ownerDesc == CD_String) {
            String strFormat = (String)constantFormat;
            strFormat = strFormat.replaceAll("%%", "");
            int numberOfConversionChars = strFormat.length() - strFormat.replaceAll("%", "").length();
            if (numberOfConversionChars == 0) {
                // just LDC the format str
                return new Result.Ldc(((String)constantFormat).replaceAll("%%", "%"));
            }
        }

        String bsmName = getBSMName(ownerDesc, methodName, isStatic, hasLocale);

        MethodTypeDesc methodTypeLessFormat = methodType.dropParameterTypes(formatArg, formatArg + 1);

        return new Result.Indy(
                DynamicCallSiteDesc.of(
                        ConstantDescs.ofCallsiteBootstrap(
                                CD_IntrinsicFactory,
                                bsmName,
                                CD_CallSite
                        ),
                        methodName,
                        methodTypeLessFormat,
                        new ConstantDesc[] { constantFormat }),
                        intrinsics.dropArg(argClassDescs.length, formatArg)
        );
    }
 }
