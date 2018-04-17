/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.Intrinsics;
import java.lang.invoke.VarHandle;
import java.lang.invoke.constant.ClassRef;
import java.lang.invoke.constant.ConstantRefs;
import java.lang.invoke.constant.DynamicConstantRef;
import java.lang.invoke.constant.MethodHandleRef;
import java.util.List;

import org.testng.annotations.Test;

import static java.lang.invoke.Intrinsics.ldc;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @test
 * @compile -XDdoConstantFold ConstantRefBootstrapsTest.java
 * @run testng ConstantRefBootstrapsTest
 * @summary integration tests for dynamic constant bootstraps and Intrinsics.ldc()
 */
@Test
public class ConstantRefBootstrapsTest {
    static final ClassRef CLASS_CONDY = ClassRef.of("java.lang.invoke.ConstantBootstraps");

    static final MethodHandleRef BSM_NULL_CONSTANT
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "nullConstant", ConstantRefs.CR_Object);
    static final MethodHandleRef BSM_PRIMITIVE_CLASS
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "primitiveClass", ConstantRefs.CR_Class);
    static final MethodHandleRef BSM_ENUM_CONSTANT
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "enumConstant", ConstantRefs.CR_Enum);
    static final MethodHandleRef BSM_GET_STATIC_FINAL_SELF
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "getStaticFinal", ConstantRefs.CR_Object);
    static final MethodHandleRef BSM_GET_STATIC_FINAL_DECL
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "getStaticFinal", ConstantRefs.CR_Object, ConstantRefs.CR_Class);
    static final MethodHandleRef BSM_INVOKE
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "invoke", ConstantRefs.CR_Object, ConstantRefs.CR_MethodHandle, ConstantRefs.CR_Object.array());
    static final MethodHandleRef BSM_VARHANDLE_FIELD
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "fieldVarHandle", ConstantRefs.CR_VarHandle, ConstantRefs.CR_Class, ConstantRefs.CR_Class);
    static final MethodHandleRef BSM_VARHANDLE_STATIC_FIELD
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "staticFieldVarHandle", ConstantRefs.CR_VarHandle, ConstantRefs.CR_Class, ConstantRefs.CR_Class);
    static final MethodHandleRef BSM_VARHANDLE_ARRAY
            = ConstantRefs.ofConstantBootstrap(CLASS_CONDY, "arrayVarHandle", ConstantRefs.CR_VarHandle, ConstantRefs.CR_Class);


    public void testNullConstant() {
        Object supposedlyNull = Intrinsics.ldc(DynamicConstantRef.of(BSM_NULL_CONSTANT, ConstantRefs.CR_Object));
        assertNull(supposedlyNull);

        supposedlyNull = Intrinsics.ldc(DynamicConstantRef.of(BSM_NULL_CONSTANT, ConstantRefs.CR_MethodType));
        assertNull(supposedlyNull);
    }


    public void testPrimitiveClass() {
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_int.descriptorString())), int.class);
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_long.descriptorString())), long.class);
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_short.descriptorString())), short.class);
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_byte.descriptorString())), byte.class);
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_char.descriptorString())), char.class);
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_float.descriptorString())), float.class);
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_double.descriptorString())), double.class);
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_boolean.descriptorString())), boolean.class);
        assertEquals(ldc(DynamicConstantRef.of(BSM_PRIMITIVE_CLASS, ConstantRefs.CR_void.descriptorString())), void.class);
    }


    public void testEnumConstant() {
        MethodHandleRef.Kind k = Intrinsics.ldc(DynamicConstantRef.of(
                BSM_ENUM_CONSTANT, "STATIC",
                ClassRef.of("java.lang.invoke.constant.MethodHandleRef$Kind")));
        assertEquals(k, MethodHandleRef.Kind.STATIC);
    }


    public void testGetStaticFinalDecl() {
        DynamicConstantRef<Class<Integer>> intClass =
                DynamicConstantRef.<Class<Integer>>of(BSM_GET_STATIC_FINAL_DECL, "TYPE", ConstantRefs.CR_Class).withArgs(ConstantRefs.CR_Integer);
        Class<Integer> c = Intrinsics.ldc(intClass);
        assertEquals(c, int.class);
    }

    public void testGetStaticFinalSelf() {
        DynamicConstantRef<Integer> integerMaxValue = DynamicConstantRef.of(BSM_GET_STATIC_FINAL_SELF, "MAX_VALUE", ConstantRefs.CR_int);
        int v = Intrinsics.ldc(integerMaxValue);
        assertEquals(v, Integer.MAX_VALUE);
    }


    public void testInvoke() {
        DynamicConstantRef<List<Integer>> list
                = DynamicConstantRef.<List<Integer>>of(BSM_INVOKE, ConstantRefs.CR_List)
                .withArgs(MethodHandleRef.of(MethodHandleRef.Kind.STATIC, ConstantRefs.CR_List, "of", ConstantRefs.CR_List, ConstantRefs.CR_Object.array()), 1, 2, 3, 4);

        List<Integer> l = ldc(list);
        assertEquals(l, List.of(1, 2, 3, 4));
    }

    public void testInvokeAsType() {
        DynamicConstantRef<Integer> valueOf = DynamicConstantRef.<Integer>of(BSM_INVOKE, ConstantRefs.CR_int)
                .withArgs(MethodHandleRef.of(MethodHandleRef.Kind.STATIC, ConstantRefs.CR_Integer, "valueOf", ConstantRefs.CR_Integer, ConstantRefs.CR_String),
                          "42");

        int v = ldc(valueOf);
        assertEquals(v, 42);
    }


    public void testVarHandleField() {
        VarHandle fh = Intrinsics.ldc(DynamicConstantRef.<VarHandle>of(BSM_VARHANDLE_FIELD, "f")
                                              .withArgs(ClassRef.of("CondyTestHelper"), ConstantRefs.CR_String));

        CondyTestHelper instance = new CondyTestHelper();
        assertEquals(null, fh.get(instance));
        fh.set(instance, "42");
        assertEquals(fh.get(instance), "42");
    }

    public void testVarHandleStaticField() {
        VarHandle sfh = Intrinsics.ldc(DynamicConstantRef.<VarHandle>of(BSM_VARHANDLE_STATIC_FIELD, "sf")
                                       .withArgs(ClassRef.of("CondyTestHelper"), ConstantRefs.CR_String));

        assertEquals(null, sfh.get());
        sfh.set("42");
        assertEquals(sfh.get(), "42");
    }

    public void testVarHandleArray() {
        VarHandle ah = Intrinsics.ldc(DynamicConstantRef.<VarHandle>of(BSM_VARHANDLE_ARRAY).withArgs(ConstantRefs.CR_String.array()));

        String[] sa = { "A" };
        assertEquals("A", ah.get(sa, 0));
        ah.set(sa, 0, "B");
        assertEquals(ah.get(sa, 0), "B");
    }
}

class CondyTestHelper {
    public static String sf;
    public String f;
}
