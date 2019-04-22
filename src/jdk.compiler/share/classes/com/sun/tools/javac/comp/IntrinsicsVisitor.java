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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.Attribute.Compound;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.intrinsics.Intrinsics;
import com.sun.tools.javac.intrinsics.IntrinsicProcessor.Result;
import com.sun.tools.javac.jvm.ClassFile;
import com.sun.tools.javac.jvm.PoolConstant;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;

import java.lang.invoke.MethodHandles;

import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.tree.JCTree.Tag.SELECT;

/**
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class IntrinsicsVisitor {
    protected static final Context.Key<IntrinsicsVisitor> intrinsicsVisitorKey = new Context.Key<>();

    private boolean enableIntrinsics;

    private final Names names;
    private final Symtab syms;
    private final Resolve rs;
    private final Types types;
    private final TreeMaker make;
    private final Intrinsics intrinsics;
    private final MethodHandles.Lookup lookup;

    private Env<AttrContext> attrEnv;
    private DiagnosticPosition nopos;

    public static IntrinsicsVisitor instance(Context context) {
        IntrinsicsVisitor instance = context.get(intrinsicsVisitorKey);

        if (instance == null)
            instance = new IntrinsicsVisitor(context);
        return instance;
    }

    IntrinsicsVisitor(Context context) {
        context.put(intrinsicsVisitorKey, this);
        Options options = Options.instance(context);
        String opt = Options.instance(context).get("intrinsify");
        enableIntrinsics = opt != null && opt.equals("all");
        names = Names.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        types = Types.instance(context);
        make = TreeMaker.instance(context);
        lookup = MethodHandles.lookup();
        intrinsics = Intrinsics.instance(context);
    }

    public JCTree analyzeTree(JCTree tree, Env<AttrContext> attrEnv) {
        if (!enableIntrinsics ||
                !(tree instanceof JCTree.JCClassDecl) ||
                tree.type.tsym.packge().modle == syms.java_base) {
            return tree;
        }
        this.attrEnv = attrEnv;
        return translator.translate(tree);
    }

    class TransformIntrinsic {
        private final MethodSymbol msym;

        TransformIntrinsic(MethodSymbol msym) {
            this.msym = msym;
        }

        private ConstantDesc makeConstantDesc(Type type) {
            Object constant = type.constValue();

            if (constant == null) {
                return null;
            } else if (type.getTag() == BOT) {
                return ConstantDescs.NULL;
            }

            return constant instanceof ConstantDesc ? (ConstantDesc)constant : null;
        }

        private Object resolveConstantDesc(ConstantDesc constantDesc) {
            try {
                return constantDesc.resolveConstantDesc(lookup);
            } catch (ReflectiveOperationException ex) {
                // do nothing
            }
            return null;
        }

        private Name fullName(ClassDesc cd) {
            if (cd.packageName().isEmpty()) {
                return names.fromString(cd.displayName());
            }

            return names.fromString(cd.packageName() + "." + cd.displayName());
        }

        public JCTree translate(JCMethodInvocation tree, JCTree.JCClassDecl currentClass) {
            ClassDesc owner = ClassDesc.of(msym.owner.toString());
            String methodName = msym.name.toString();
            MethodTypeDesc methodTypeDesc = MethodTypeDesc.ofDescriptor(signature(msym.type));
            boolean isStatic = (msym.flags() & STATIC) != 0;
            List<JCExpression>args = tree.args;
            int argSize = args.size();
            int offset = isStatic ? 0 : 1;

            int allArgsSize = offset + argSize;
            JCExpression[] allArgs = new JCExpression[allArgsSize];
            ClassDesc[] argClassDescs = new ClassDesc[allArgsSize];
            ConstantDesc[] constantArgs = new ConstantDesc[allArgsSize];

            if (!isStatic) {
                JCExpression qualifierExpr = tree.meth.hasTag(SELECT) ?
                        ((JCFieldAccess)tree.meth).selected :
                        make.at(tree).This(currentClass.sym.erasure(types));
                allArgs[0] = qualifierExpr;
                argClassDescs[0] = ClassDesc.ofDescriptor(signature(qualifierExpr.type));
                constantArgs[0] = makeConstantDesc(qualifierExpr.type);
            }

            for (int i = 0; i < argSize; i++) {
                JCExpression arg = args.get(i);

                if (arg == null) {
                    return tree;
                }

                int io = i + offset;
                allArgs[io] = arg;
                argClassDescs[io] = ClassDesc.ofDescriptor(signature(arg.type));
                constantArgs[io] = makeConstantDesc(arg.type);
            }

            // Compiler env object
            Result result = intrinsics.tryIntrinsify(
                    owner,
                    methodName,
                    methodTypeDesc,
                    isStatic,
                    argClassDescs,
                    constantArgs
            );

            switch (result.getKind()) {
                case NONE: return tree;
                case LDC:
                    Result.Ldc ldc = (Result.Ldc)result;
                    Object constant = resolveConstantDesc(ldc.constant());

                    return constant == null ? make.Literal(BOT, null).setType(syms.botType) :
                            make.Literal(constant);
                case INDY:
                    Result.Indy indy = (Result.Indy)result;
                    DynamicCallSiteDesc callSite = indy.indy();
                    String invocationName = callSite.invocationName();
                    DirectMethodHandleDesc bootstrapMethod = (DirectMethodHandleDesc)callSite.bootstrapMethod();
                    ClassDesc ownerClass = bootstrapMethod.owner();
                    String bootstrapName = bootstrapMethod.methodName();
                    List<Object> staticArgs = List.nil();

                    for (ConstantDesc constantDesc : callSite.bootstrapArgs()) {
                        staticArgs = staticArgs.append(resolveConstantDesc(constantDesc));
                    }

                    List<JCExpression> indyArgs = List.nil();

                    for (int i : indy.args()) {
                        indyArgs = indyArgs.append(allArgs[i]);
                    }

                    List<Type> argTypes = List.nil();

                    for (JCExpression arg : indyArgs) {
                        if (arg.type == syms.botType) {
                            argTypes = argTypes.append(syms.objectType);
                        } else {
                            argTypes = argTypes.append(arg.type);
                        }
                    }

                    Type returnType = msym.type.getReturnType();
                    MethodType indyType = new MethodType(argTypes, returnType,  List.nil(), syms.methodClass);
                    ClassSymbol classSymbol = syms.enterClass(msym.packge().modle, fullName(ownerClass));

                    return makeDynamicCall(
                            new SimpleDiagnosticPosition(tree.pos),
                            classSymbol.type,
                            names.fromString(bootstrapName),
                            staticArgs,
                            indyType,
                            indyArgs,
                            names.fromString(invocationName)
                    );
                    default:
                        throw new AssertionError("tryIntrinsifyMethod result unknown");
            }
        }
    }

    Translator translator = new Translator();
    class Translator extends TreeTranslator {
        JCTree.JCClassDecl currentClass;

        @Override
        public void visitApply(JCMethodInvocation tree) {
            super.visitApply(tree);
            Name methName = TreeInfo.name(tree.meth);

            if (methName == names._this || methName == names._super) {
                return;
            }

            MethodSymbol msym = (MethodSymbol)TreeInfo.symbol(tree.meth);
            Compound attr = msym.attribute(syms.intrinsicCandidateType.tsym);

            if (attr != null) {
                TransformIntrinsic transform = new TransformIntrinsic(msym);
                result = transform.translate(tree, currentClass);
            }
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            JCTree.JCClassDecl previousClass = currentClass;
            try {
                currentClass = tree;
                super.visitClassDef(tree);
            } finally {
                currentClass = previousClass;
            }
        }
    }

    private JCExpression makeDynamicCall(DiagnosticPosition pos, Type site, Name bsmName,
                                         List<Object> staticArgs, MethodType indyType,
                                         List<JCExpression> indyArgs,
                                         Name methName) {
        int prevPos = make.pos;
        try {
            make.at(pos);
            List<Type> bsm_staticArgs = List.of(syms.methodHandleLookupType,
                    syms.stringType,
                    syms.methodTypeType).appendList(bsmStaticArgToTypes(staticArgs));
            Symbol bsm = rs.resolveQualifiedMethod(pos, attrEnv, site,
                    bsmName, bsm_staticArgs, List.nil());
            PoolConstant.LoadableConstant[] loadableConstantsArr = objToLoadableConstantArr(staticArgs);
            DynamicMethodSymbol dynSym =
                    new DynamicMethodSymbol(methName,
                            syms.noSymbol,
                            ((MethodSymbol)bsm).asHandle(),
                            indyType,
                            loadableConstantsArr);

            JCFieldAccess qualifier = make.Select(make.QualIdent(site.tsym), bsmName);
            qualifier.sym = dynSym;
            qualifier.type = indyType;
            JCMethodInvocation proxyCall = make.Apply(List.nil(), qualifier, indyArgs);
            proxyCall.type = indyType.getReturnType();

            return proxyCall;
        } finally {
            make.at(prevPos);
        }
    }

    private List<Type> bsmStaticArgToTypes(List<Object> args) {
        ListBuffer<Type> argtypes = new ListBuffer<>();

        for (Object arg : args) {
            argtypes.append(bsmStaticArgToType(arg));
        }

        return argtypes.toList();
    }

    private Type bsmStaticArgToType(Object arg) {
        if (arg instanceof ClassSymbol) {
            return syms.classType;
        } else if (arg instanceof Integer) {
            return syms.intType;
        } else if (arg instanceof Long) {
            return syms.longType;
        } else if (arg instanceof Float) {
            return syms.floatType;
        } else if (arg instanceof Double) {
            return syms.doubleType;
        } else if (arg instanceof String) {
            return syms.stringType;
        } else {
            Assert.error("bad static arg " + arg);
            return null;
        }
    }

    private PoolConstant.LoadableConstant[] objToLoadableConstantArr(List<Object> args) {
        PoolConstant.LoadableConstant[] loadableConstants = new PoolConstant.LoadableConstant[args.size()];
        int index = 0;
        for (Object arg : args) {
            loadableConstants[index++] = objToLoadableConstant(arg);
        }
        return loadableConstants;
    }

    PoolConstant.LoadableConstant objToLoadableConstant(Object o) {
        if (o instanceof Integer) {
            return PoolConstant.LoadableConstant.Int((int)o);
        } else if (o instanceof Float) {
            return PoolConstant.LoadableConstant.Float((float)o);
        } else if (o instanceof Long) {
            return PoolConstant.LoadableConstant.Long((long)o);
        } else if (o instanceof Double) {
            return PoolConstant.LoadableConstant.Double((double)o);
        } else if (o instanceof String) {
            return PoolConstant.LoadableConstant.String((String)o);
        } else {
            throw new AssertionError("unexpected constant: " + o);
        }
    }

    private String signature(Type type) {
        SignatureGenerator generator = new SignatureGenerator();
        generator.assembleSig(type.getTag() == BOT ? syms.objectType : type);

        return generator.toString();
    }

    private class SignatureGenerator extends Types.SignatureGenerator {
        StringBuilder sb = new StringBuilder();

        SignatureGenerator() {
            super(types);
        }

        @Override
        protected void append(char ch) {
            sb.append(ch);
        }

        @Override
        protected void append(byte[] ba) {
            sb.append(new String(ba));
        }

        @Override
        protected void append(Name name) {
            sb.append(name.toString());
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
