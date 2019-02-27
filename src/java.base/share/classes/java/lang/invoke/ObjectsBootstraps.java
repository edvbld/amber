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

package java.lang.invoke;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;

/**
 * Bootstrapping support for Objects intrinsics.
 */
public final class ObjectsBootstraps {
    /**
     * Bootstrap for Objects intrinsics.
     * @param lookup         MethodHandles lookup
     * @param name           Name of method
     * @param methodType     Method signature
     * @return Callsite for intrinsic method
     */
    public static CallSite hashBootstrap(MethodHandles.Lookup lookup,
                                         String name,
                                         MethodType methodType) {
        assert methodType.returnType() == int.class;
        initialize();
        return new ConstantCallSite(createHashMethodHandle(methodType.parameterArray()));
    }

    static Map<Class<?>, MethodHandle> HASH_METHODS;
    static MethodHandle HASH_OBJECT;
    static MethodHandle ALL_HASHES_MH;

    static void initialize() {
        if (HASH_METHODS == null) {
            HASH_METHODS = new HashMap<>();
            HASH_METHODS.put(boolean.class, findHashMethod(boolean.class));
            HASH_METHODS.put(byte.class, findHashMethod(byte.class));
            HASH_METHODS.put(char.class, findHashMethod(char.class));
            HASH_METHODS.put(short.class, findHashMethod(short.class));
            HASH_METHODS.put(int.class, findHashMethod(int.class));
            HASH_METHODS.put(float.class, findHashMethod(float.class));
            HASH_METHODS.put(double.class, findHashMethod(double.class));

            HASH_OBJECT = findHashMethod(Object.class);
            try {
                ALL_HASHES_MH = IMPL_LOOKUP.findStatic(ObjectsBootstraps.class, "combineHashes", MethodType.methodType(int.class, int[].class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {

            }
        }
    }

    static MethodHandle createHashMethodHandle(Class<?>... argTypes) {
        Class<?>[] intArgs = new Class<?>[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            intArgs[i] = int.class;
        }
        MethodType methodType = MethodType.methodType(int.class, intArgs);
        MethodHandle mhHash = ALL_HASHES_MH.asType(methodType);
        MethodHandle[] filters = new MethodHandle[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            filters[i] = getHashMethod(argTypes[i]);
        }
        mhHash = MethodHandles.filterArguments(mhHash, 0, filters);
        return mhHash;
    }

    static int combineHashes(int... hashes) {
        int numberOfHashes = hashes.length;
        int multiplier = 1;
        int result = 0;
        for (int i = numberOfHashes - 1; i >= 0; i--) {
            result += multiplier * hashes[i];
            multiplier *= 31;
        }
        result += multiplier;
        return result;
    }

    static MethodHandle getHashMethod(Class<?> type) {
        MethodHandle hashMH = HASH_METHODS.get(type);
        if (hashMH == null) {
            hashMH = HASH_OBJECT;
            hashMH = hashMH.asType(MethodType.methodType(int.class, type));
        }
        return hashMH;
    }

    static MethodHandle findHashMethod(Class<?> cls) {
        try {
            MethodType mt = MethodType.methodType(int.class, cls);
            return IMPL_LOOKUP.findStatic(ObjectsBootstraps.class, "hash", mt);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            return null;
        }
     }

    static int hash(boolean value) {
        return Boolean.hashCode(value);
    }

    static int hash(char value) {
        return value;
    }

    static int hash(byte value) {
        return value;
    }

    static int hash(short value) {
        return value;
    }

    static int hash(int value) {
        return value;
    }

    static int hash(float value) {
        return Float.hashCode(value);
    }

    static int hash(double value) {
        return Double.hashCode(value);
    }

    static int hash(Object value) {
        return (value == null ? 0 : value.hashCode());
    }
}
