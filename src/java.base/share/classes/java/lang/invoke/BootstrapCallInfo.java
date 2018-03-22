/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Arrays;
import java.util.List;

/**
 * An interface providing full static information about a particular
 * call to a
 * <a href="package-summary.html#bsm">bootstrap method</a> of an
 * dynamic call site or dynamic constant.
 * This information includes the method itself, the associated
 * name and type, and any associated static arguments.
 * <p>
 * If a bootstrap method declares exactly two arguments, and is
 * not of variable arity, then it is fed only two arguments by
 * the JVM, the {@linkplain Lookup lookup object} and an instance
 * of {@code BootstrapCallInfo} which supplies the rest of the
 * information about the call.
 * <p>
 * The API for accessing the static arguments allows the bootstrap
 * method to reorder the resolution (in the constant pool) of the
 * static arguments, and to catch errors resulting from the resolution.
 * This mode of evaluation <em>pulls</em> bootstrap parameters from
 * the JVM under control of the bootstrap method, as opposed to
 * the JVM <em>pushing</em> parameters to a bootstrap method
 * by resolving them all before the bootstrap method is called.
 * @apiNote
 * <p>
 * The {@linkplain Lookup lookup object} is <em>not</em> included in this
 * bundle of information, so as not to obscure the access control
 * logic of the program.
 * In cases where there are many thousands of parameters, it may
 * be preferable to pull their resolved values, either singly or in
 * batches, rather than wait until all of them have been resolved
 * before a constant or call site can be used.
 * <p>
 * A push mode bootstrap method can be adapted to a pull mode
 * bootstrap method, and vice versa.  For example, this generic
 * adapter pops a push-mode bootstrap method from the beginning
 * of the static argument list, eagerly resolves all the remaining
 * static arguments, and invokes the popped method in push mode.
 * The callee has no way of telling that it was not called directly
 * from the JVM.
 * <blockquote><pre>{@code
static Object genericBSM(Lookup lookup, BootstrapCallInfo<Object> bsci)
    throws Throwable {
  ArrayList<Object> args = new ArrayList<>();
  args.add(lookup);
  args.add(bsci.invocationName());
  args.add(bsci.invocationType());
  MethodHandle bsm = (MethodHandle) bsci.get(0);
  List<Object> restOfArgs = bsci.asList().subList(1, bsci.size());
  // the next line eagerly resolves all remaining static arguments:
  args.addAll(restOfArgs);
  return bsm.invokeWithArguments(args);
}
 * }</pre></blockquote>
 *
 * <p>
 * In the other direction, here is a combinator which pops
 * a pull-mode bootstrap method from the beginning of a list of
 * static argument values (already resolved), reformats all of
 * the arguments into a pair of a lookup and a {@code BootstrapCallInfo},
 * and invokes the popped method.  Again the callee has no way of
 * telling it was not called directly by the JVM, except that
 * all of the constant values will appear as resolved.
 * Put another way, if any constant fails to resolve, the
 * callee will not be able to catch the resulting error,
 * since the error will be thrown by the JVM before the
 * bootstrap method is entered.
 * <blockquote><pre>{@code
static Object genericBSM(Lookup lookup, String name, Object type,
                         MethodHandle bsm, Object... args)
    throws Throwable {
  BootstrapCallInfo<Object> bsci = makeBootstrapCallInfo(bsm, name, type, args);
  return bsm.invoke(lookup, bsci);
}
 * }</pre></blockquote>
 *
 * @since 11
 */
public
interface BootstrapCallInfo<T> extends ConstantGroup {

    /// Access

    /**
     * Returns the number of static arguments.
     * @return the number of static arguments
     */
    int size();

    /**
     * Returns the selected static argument, resolving it if necessary.
     * Throws a linkage error if resolution proves impossible.
     * @param index which static argument to select
     * @return the selected static argument
     * @throws LinkageError if the selected static argument needs resolution
     * and cannot be resolved
     */
    Object get(int index) throws LinkageError;

    /**
     * Returns the selected static argument,
     * or the given sentinel value if there is none available.
     * If the static argument cannot be resolved, the sentinel will be returned.
     * If the static argument can (perhaps) be resolved, but has not yet been resolved,
     * then the sentinel <em>may</em> be returned, at the implementation's discretion.
     * To force resolution (and a possible exception), call {@link #get(int)}.
     * @param index the selected constant
     * @param ifNotPresent the sentinel value to return if the static argument is not present
     * @return the selected static argument, if available, else the sentinel value
     */
    Object get(int index, Object ifNotPresent);

    /**
     * Returns an indication of whether a static argument may be available.
     * If it returns {@code true}, it will always return true in the future,
     * and a call to {@link #get(int)} will never throw an exception.
     * <p>
     * After a normal return from {@link #get(int)} or a present
     * value is reported from {@link #get(int,Object)}, this method
     * must always return true.
     * <p>
     * If this method returns {@code false}, nothing in particular
     * can be inferred, since the query only concerns the internal
     * logic of the {@code BootstrapCallInfo} object which ensures that a
     * successful query to a constant will always remain successful.
     * The only way to force a permanent decision about whether
     * a static argument is available is to call {@link #get(int)} and
     * be ready for an exception if the constant is unavailable.
     * @param index the selected constant
     * @return {@code true} if the selected static argument is known by
     *     this object to be present, {@code false} if it is known
     *     not to be present or
     */
    boolean isPresent(int index);


    /// Views

    /**
     * Create a view on the static arguments as a {@link List} view.
     * Any request for a static argument through this view will
     * force resolution.
     * @return a {@code List} view on the static arguments which will force resolution
     */
    default List<Object> asList() {
        return new AbstractConstantGroup.AsList(this, 0, size());
    }

    /**
     * Create a view on the static argument as a {@link List} view.
     * Any request for a static argument through this view will
     * return the given sentinel value, if the corresponding
     * call to {@link #get(int,Object)} would do so.
     * @param ifNotPresent the sentinel value to return if a static argument is not present
     * @return a {@code List} view on the static arguments which will not force resolution
     */
    default List<Object> asList(Object ifNotPresent) {
        return new AbstractConstantGroup.AsList(this, 0, size(), ifNotPresent);
    }


    /// Bulk operations

    /**
     * Copy a sequence of static arguments into a given buffer.
     * This is equivalent to {@code end-offset} separate calls to {@code get},
     * for each index in the range from {@code offset} up to but not including {@code end}.
     * For the first static argument that cannot be resolved,
     * a {@code LinkageError} is thrown, but only after
     * preceding static arguments have been stored.
     * @param start index of first static argument to retrieve
     * @param end limiting index of static arguments to retrieve
     * @param buf array to receive the requested static arguments
     * @param pos position in the array to offset storing the static arguments
     * @return the limiting index, {@code end}
     * @throws LinkageError if a static argument cannot be resolved
     */
    default int copyArguments(int start, int end,
                              Object[] buf, int pos)
            throws LinkageError
    {
        int bufBase = pos - start;  // buf[bufBase + i] = get(i)
        for (int i = start; i < end; i++) {
            buf[bufBase + i] = get(i);
        }
        return end;
    }

    /**
     * Copy a sequence of static arguments into a given buffer.
     * This is equivalent to {@code end-offset} separate calls to {@code get},
     * for each index in the range from {@code offset} up to but not including {@code end}.
     * Any static arguments that cannot be resolved are replaced by the
     * given sentinel value.
     * @param start index of first static argument to retrieve
     * @param end limiting index of static arguments to retrieve
     * @param buf array to receive the requested values
     * @param pos position in the array to offset storing the static arguments
     * @param ifNotPresent sentinel value to store if a static argument is not available
     * @return the limiting index, {@code end}
     * @throws LinkageError if {@code resolve} is true and a static argument cannot be resolved
     */
    default int copyConstants(int start, int end,
                              Object[] buf, int pos,
                              Object ifNotPresent) {
        int bufBase = pos - start;  // buf[bufBase + i] = get(i)
        for (int i = start; i < end; i++) {
            buf[bufBase + i] = get(i, ifNotPresent);
        }
        return end;
    }


    /** Returns the bootstrap method for this call.
     * @return the bootstrap method
     */
    MethodHandle bootstrapMethod();

    /** Returns the method name or constant name for this call.
     * @return the method name or constant name
     */
    String invocationName();

    /** Returns the method type or constant type for this call.
     * @return the method type or constant type
     */
    T invocationType();

    /**
     * Make a new bootstrap call descriptor with the given components.
     * @param bsm bootstrap method
     * @param name invocation name
     * @param type invocation type
     * @param args the additional static arguments for the bootstrap method
     * @param <T> the type of the invocation type, either {@link MethodHandle} or {@link Class}
     * @return a new bootstrap call descriptor with the given components
     */
    static <T> BootstrapCallInfo<T> makeBootstrapCallInfo(MethodHandle bsm,
                                                          String name,
                                                          T type,
                                                          Object... args) {
        AbstractConstantGroup.BSCIWithCache<T> bsci = new AbstractConstantGroup.BSCIWithCache<>(bsm, name, type, args.length);
        final Object NP = AbstractConstantGroup.BSCIWithCache.NOT_PRESENT;
        bsci.initializeCache(Arrays.asList(args), NP);
        return bsci;
    }

    /**
     * Invoke a bootstrap method handle with arguments obtained by resolving
     * the sequence of constants supplied by a given bootstrap call descriptor,
     * {@code bci}.
     * The first argument to the method will be {@code lookup}.
     * The second argument will be the invocation name of {@code bci}.
     * The third argument will be the invocation type of {@code bci}.
     * The fourth and subsequent arguments (if any) will be the resolved
     * constants, in order, supplied by {@code bci}.
     * <p>
     * @apiNote
     * This method behaves like the following but may be more optimal:
     * <blockquote><pre>{@code
     *   ArrayList<Object> args = new ArrayList<>();
     *   args.add(lookup);
     *   args.add(bsci.invocationName());
     *   args.add(bsci.invocationType());
     *   List<Object> constantArgs = bsci.asList();
     *   args.addAll(constantArgs);
     *   return handle.invokeWithArguments(args);
     * }</pre></blockquote>
     *
     * @param handle the bootstrap method handle to be invoked with resolved
     *        constants supplied by {@code bci}
     * @param lookup the lookup
     * @param bsci the bootstrap call descriptor
     * @return the result of invocation
     * @throws Throwable if an error occurs when resolving the constants from
     *         the bootstrap call descriptor or invoking the method handle
     */
    // @@@ More stuff to add as api note
    // This method is static so that it's possible to look it up and bind
    // to a method handle, thereby viewing that method handle as if accepts
    // a BootstrapCallInfo
    static Object invokeFromCallInfoToArguments(MethodHandle handle,
                                                MethodHandles.Lookup lookup,
                                                BootstrapCallInfo<?> bsci) throws Throwable {
        int argc = bsci.size();
        switch (argc) {
            case 0:
                return handle.invoke(lookup, bsci.invocationName(), bsci.invocationType());
            case 1:
                return handle.invoke(lookup, bsci.invocationName(), bsci.invocationType(),
                                     bsci.get(0));
            case 2:
                return handle.invoke(lookup, bsci.invocationName(), bsci.invocationType(),
                                     bsci.get(0), bsci.get(1));
            case 3:
                return handle.invoke(lookup, bsci.invocationName(), bsci.invocationType(),
                                     bsci.get(0), bsci.get(1), bsci.get(2));
            case 4:
                return handle.invoke(lookup, bsci.invocationName(), bsci.invocationType(),
                                     bsci.get(0), bsci.get(1), bsci.get(2), bsci.get(3));
            case 5:
                return handle.invoke(lookup, bsci.invocationName(), bsci.invocationType(),
                                     bsci.get(0), bsci.get(1), bsci.get(2), bsci.get(3), bsci.get(4));
            case 6:
                return handle.invoke(lookup, bsci.invocationName(), bsci.invocationType(),
                                     bsci.get(0), bsci.get(1), bsci.get(2), bsci.get(3), bsci.get(4), bsci.get(5));
            default:
                final int NON_SPREAD_ARG_COUNT = 3;  // (lookup, name, type)
                final int MAX_SAFE_SIZE = MethodType.MAX_MH_ARITY / 2 - NON_SPREAD_ARG_COUNT;
                if (argc >= MAX_SAFE_SIZE) {
                    // to be on the safe side, use invokeWithArguments which handles jumbo lists
                    Object[] newargv = new Object[NON_SPREAD_ARG_COUNT + argc];
                    newargv[0] = lookup;
                    newargv[1] = bsci.invocationName();
                    newargv[2] = bsci.invocationType();
                    bsci.copyArguments(0, argc, newargv, NON_SPREAD_ARG_COUNT);
                    return handle.invokeWithArguments(newargv);
                }
                MethodType invocationType = MethodType.genericMethodType(NON_SPREAD_ARG_COUNT + argc);
                MethodHandle typedBSM = handle.asType(invocationType);
                MethodHandle spreader = invocationType.invokers().spreadInvoker(NON_SPREAD_ARG_COUNT);
                Object[] argv = new Object[argc];
                bsci.copyArguments(0, argc, argv, 0);
                return spreader.invokeExact(typedBSM, (Object) lookup, (Object) bsci.invocationName(), bsci.invocationType(), argv);
        }
    }

    /**
     * Invoke a bootstrap method handle with a bootstrap call descriptor
     * argument composed from a given sequence of arguments.
     * <p>
     * @apiNote
     * This method behaves like the following but may be more optimal:
     * <blockquote><pre>{@code
     *   BootstrapCallInfo<Object> bsci = makeBootstrapCallInfo(handle, name, type, args);
     *   return handle.invoke(lookup, bsci);
     * }</pre></blockquote>
     *
     * @param handle the bootstrap method handle to be invoked with a bootstrap
     *        call descriptor composed from the sequence of arguments
     * @param lookup the lookup
     * @param name the method name or constant name
     * @param type the method type or constant type
     * @param args the sequence of arguments
     * @return the result of invocation
     * @throws Throwable if an error occurs when invoking the method handle
     */
    static Object invokeFromArgumentsToCallInfo(MethodHandle handle,
                                                MethodHandles.Lookup lookup,
                                                String name,
                                                Object type,
                                                Object... args) throws Throwable {
        BootstrapCallInfo<?> bsci = makeBootstrapCallInfo(handle, name, type, args);
        return handle.invoke(lookup, bsci);
    }
}