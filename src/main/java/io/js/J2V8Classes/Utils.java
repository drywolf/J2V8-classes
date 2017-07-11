package io.js.J2V8Classes;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.V8Value;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by Brown on 4/26/16.
 */
public class Utils {

    private static Logger logger = Logger.getLogger("Utils");

    public static <T> String printArray(T[] array)
    {
        return String.join(",",
            Arrays.stream(array)
            .map(arg ->  arg == null ? "null" : arg.toString())
            .toArray(String[]::new)
        );
    }

    public static String getScriptSource(ClassLoader classLoader, String path) {
        InputStream in = classLoader.getResourceAsStream(path);
        try {
            return IOUtils.toString(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static Class lowestCommonClass(Class c1, Class c2) {
        if (c1 == c2) {
            return c1;
        }

        if (c1.isAssignableFrom(c2)) {
            return c1;
        }

        if (c2.isAssignableFrom(c1)) {
            return c2;
        }

        return Object.class;
    }


    public static Object[] v8arrayToObjectArray(V8Array v8array) {
        return v8arrayToObjectArray(v8array, 0, v8array.length());
    }
    public static Object[] v8arrayToObjectArray(V8Array v8array, int start) {
        return v8arrayToObjectArray(v8array, start, v8array.length());
    }
    public static Object[] v8arrayToObjectArray(V8Array v8array, int start, int end) {
        Object[] res = new Object[end - start];
        logger.fine("v8arrayToObjectArray: " + (end - start));

        Class lowestCommonClass = null;
        boolean hasUnanimousClass = true;

        for (int i = start; i < end; i++) {
            Object o = v8array.get(i);
            int idx = i - start;
            res[idx] = o;

            // Replace V8Value instances with their java counterparts
            if (o instanceof V8Array) {
                V8Array v8o = (V8Array) o;
                logger.fine("Found array, recursing...");
                res[idx] = v8arrayToObjectArray(v8o);
//                v8o.release();
            } else if (o instanceof V8Object) {
                V8Object v8o = (V8Object) o;
                if (!v8o.isUndefined() && v8o.contains("__javaInstance")) {
                    int instHash = v8o.getInteger("__javaInstance");
                    Object inst = getInstance(instHash);
                    if (inst == null) {
                        logger.warning("v8arrayToObjectArray: unknown instance: " + instHash);
                    } else {
                        res[idx] = inst;
//                        v8o.release();
                    }
                }
            }

            if (res[idx] != null) {
                Class resClz = res[idx].getClass();
                logger.fine("resClz: " + i + ": " + resClz.getName());
                if (hasUnanimousClass) {
                    if (lowestCommonClass == null) {
                        lowestCommonClass = resClz;
                    } else {
                        lowestCommonClass = lowestCommonClass(lowestCommonClass, resClz);
                    }
                }
            }
        }

        if (lowestCommonClass != null && hasUnanimousClass) {
            logger.fine("lowestCommonClass: " + lowestCommonClass.getName());
            try {
                // TODO: this seems like cancer
                String lccName = lowestCommonClass.getName();

                lccName = V8JavaClasses.classAliasFor(lccName);

                if (!lowestCommonClass.isArray()) {
                    lccName = "[L" + lccName + ";";
                } else {
                    lccName = "[" + lccName;
                }

                Class<? extends Object[]> newClz = (Class<? extends Object[]>) Class.forName(lccName);
                return Arrays.copyOf(res, res.length, newClz);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return res;
    }

    public static Object[] toObjectArray(Object orig) {
        Class clz = orig.getClass();
        if (!clz.isArray()) {
            logger.warning("Provided class is not an array: " + clz.getName());
            return null;
        }

        Class componentClz = clz.getComponentType();
        if (componentClz.isPrimitive()) {
            // TODO: surely there is a better way
            if (int.class.equals(componentClz)) {
                return ArrayUtils.toObject((int[]) orig);
            }
            if (long.class.equals(componentClz)) {
                return ArrayUtils.toObject((long[]) orig);
            }
            if (char.class.equals(componentClz)) {
                return ArrayUtils.toObject((char[]) orig);
            }
            if (short.class.equals(componentClz)) {
                return ArrayUtils.toObject((short[]) orig);
            }
            if (boolean.class.equals(componentClz)) {
                return ArrayUtils.toObject((boolean[]) orig);
            }
            if (byte.class.equals(componentClz)) {
                return ArrayUtils.toObject((byte[]) orig);
            }
        }
        return (Object[]) orig;
    }

    public static Object[] matchExecutableParams(Executable exc, Object[] params) {
        Object[] res = new Object[params.length];
        Class[] excParamTypes = exc.getParameterTypes();

        for (int i = 0; i < params.length; i++) {

            // TODO: proper handling & testing of null params
            if (params[i] == null)
                continue;

            Class need = excParamTypes[i];
            Class got = params[i].getClass();

            if (Boolean.class.equals(need) || boolean.class.equals(need)) {
                res[i] = ((Boolean) params[i]).booleanValue();
                continue;
            }

            if (isNumber(need)) {
                if (int.class.equals(need)) {
                    res[i] = ((Number) params[i]).intValue();
                    continue;
                }
                if (long.class.equals(need)) {
                    res[i] = ((Number) params[i]).longValue();
                    continue;
                }
                if (float.class.equals(need)) {
                    res[i] = ((Number) params[i]).floatValue();
                    continue;
                }
                if (double.class.equals(need)) {
                    res[i] = ((Number) params[i]).doubleValue();
                    continue;
                }
            }

            if (need.isArray()) {
                if (int[].class.equals(need) && Integer[].class.equals(got)) {
                    res[i] = ArrayUtils.toPrimitive((Integer[]) params[i]);
                    continue;
                }
                if (long[].class.equals(need) && Long[].class.equals(got)) {
                    res[i] = ArrayUtils.toPrimitive((Long[]) params[i]);
                    continue;
                }
                if (float[].class.equals(need) && Float[].class.equals(got)) {
                    res[i] = ArrayUtils.toPrimitive((Float[]) params[i]);
                    continue;
                }
                if (double[].class.equals(need) && Double[].class.equals(got)) {
                    res[i] = ArrayUtils.toPrimitive((Double[]) params[i]);
                    continue;
                }
                if (char[].class.equals(need) && Character[].class.equals(got)) {
                    res[i] = ArrayUtils.toPrimitive((Character[]) params[i]);
                    continue;
                }
                if (short[].class.equals(need) && Short[].class.equals(got)) {
                    res[i] = ArrayUtils.toPrimitive((Short[]) params[i]);
                    continue;
                }
                if (boolean[].class.equals(need) && Boolean[].class.equals(got)) {
                    res[i] = ArrayUtils.toPrimitive((Boolean[]) params[i]);
                    continue;
                }
                if (byte[].class.equals(need) && Byte[].class.equals(got)) {
                    res[i] = ArrayUtils.toPrimitive((Byte[]) params[i]);
                    continue;
                }
            }

            if (!need.isPrimitive() && need != V8Object.class && params[i] instanceof V8Object && ((V8Object)params[i]).isUndefined()) {
                res[i] = null;
                continue;
            }

            res[i] = need.cast(params[i]);
        }
        return res;
    }

    public static boolean isNumber(Class clz) {
        return clz == int.class || clz == float.class || clz == double.class || clz == long.class || Number.class.isAssignableFrom(clz);
    }

    /** Note: should only be used internally, not a true primative match */
    public static boolean primativeMatch(Class c1, Class c2) {
        if (isNumber(c1) && isNumber(c2)) {
            return true;
        }
        return (char.class.equals(c1) && Character.class.equals(c2))
                || (short.class.equals(c1) && Short.class.equals(c2))
                || (boolean.class.equals(c1) && Boolean.class.equals(c2))
                || (byte.class.equals(c1) && Byte.class.equals(c2));
    }

    public static Executable findMatchingExecutable(Executable[] excs, Object[] params, String name) {
        // TODO: support varargs without passing as array
        logger.fine("Finding method...  \"" + name + "\" (total " + excs.length + ")");

        Class[] paramTypes = Utils.getArrayClasses(params);
        logger.fine("Arg classes: " + printArray(paramTypes));
        Type[] paramTypes2 = new Type[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            // TODO: proper handling & testing of null params
            if (params[i] != null)
                paramTypes2[i] = params[i].getClass();
        }
        logger.fine("Arg types: " + printArray(paramTypes2));

        for (int i = 0; i < excs.length; i++) {
            if (name != null && excs[i].getName() != name) {
                continue;
            }

            Class[] excParamTypes = excs[i].getParameterTypes();
            logger.fine("> Testing against " + excs[i].getName() + "(args: " + printArray(excParamTypes) + ")");
            Type[] excParamTypes2 = excs[i].getGenericParameterTypes();
            logger.fine(">> : " + printArray(excParamTypes2));
            if (excParamTypes.length != paramTypes.length) {
                continue;
            }

            boolean match = true;

            for (int j = 0; j < paramTypes.length; j++) {
                Class need = excParamTypes[j];
                Class got = paramTypes[j];

                // TODO: proper handling & testing of null params
                if (need == null || got == null || need.isAssignableFrom(got) || need.equals(got)) {
                    continue;
                }

                if (need.isArray() && got.isArray()) {
                    Class need0 = need.getComponentType();
                    Class got0 = got.getComponentType();
                    if (need0.isAssignableFrom(got0)) {
                        continue;
                    }
                    if (primativeMatch(need0, got0)) {
                        continue;
                    }
                }

                // Check primatives
                if (need.isPrimitive() && primativeMatch(need, got)) {
                    continue;
                }

                // Soft match numbers
                if (isNumber(need) && isNumber(got)) {
                    continue;
                }

                if (!need.isPrimitive() && need != V8Object.class && params[j] instanceof V8Object && ((V8Object)params[j]).isUndefined()) {
                    continue;
                }

                match = false;
            }
            if (match) {
                logger.fine("> Found: " + excs[i]);
                return excs[i];
            }
        }

        logger.warning("Could not infer executable, parameter class signature not found");
        return null;
    }


    public static void releaseAllFor(V8 runtime) {
        logger.fine("releaseAllFor: " + runtime);
        Iterator<Map.Entry<Integer, V8Object>> it = jsInstanceMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, V8Object> pair = (Map.Entry)it.next();
            V8Object jso = pair.getValue();
            if (jso.getRuntime() == runtime) {
                int hash = pair.getKey();
                logger.fine("> releasing: " + hash);
                jso.release();
                javaInstanceMap.remove(hash);
                it.remove();
            }
        }
        logger.fine("> items still left: " + jsInstanceMap.size());
    }


    public static V8Object toV8Object(V8 v8, Object o) {
        V8Object res = new V8Object(v8);
        if (o == null) {
            res.addNull("v");
            return res;
        }

        Class clz = o.getClass();
        if (clz == Boolean.class) {
            res.add("v", (boolean) o);
        } else if (clz == Double.class) {
            res.add("v", (double) o);
        } else if (clz == Integer.class) {
            res.add("v", (int) o);
        } else if (clz == String.class) {
            res.add("v", (String) o);
        } else if (clz == CharSequence.class) {
            res.add("v", o.toString());
        } else if (clz.isArray()) {
            logger.fine("> Array! " + clz);
            Object[] oarr = toObjectArray(o);
            V8Array arr = new V8Array(v8);
            for (int i = 0; i < oarr.length; i++) {
                arr.push(toV8Object(v8, oarr[i]));
            }
            res.add("v", arr);
            arr.release();
        } else if (o instanceof V8Value) {
            res.add("v", (V8Value) o);
        } else if (o instanceof Object) {
            logger.fine("> Class! " + clz);
            V8Object jsInst = Utils.getV8ObjectForObject(v8, o);
            res.add("v", jsInst);
        } else {
            logger.warning("> Unknown type! " + clz);
        }
        return res;
    }

    public static String UnboxReturnType(String returnType)
    {
        switch (returnType)
        {
            case "byte": return "Byte";
            case "short": return "Short";
            case "int": return "Integer";
            case "long": return "Long";
            case "float": return "Float";
            case "double": return "Double";
            case "boolean": return "Boolean";
            case "char": return "Character";
            default: return returnType;
        }
    }

    public static String UnboxReturnValue(String returnType)
    {
        switch (returnType)
        {
            case "byte": return ".byteValue()";
            case "short": return ".shortValue()";
            case "int": return ".intValue()";
            case "long": return ".longValue()";
            case "float": return ".floatValue()";
            case "double": return ".doubleValue()";
            case "boolean": return ".booleanValue()";
            case "char": return ".charValue()";
            default: return "";
        }
    }

    private static HashMap<Integer, Object> javaInstanceMap = new HashMap<Integer, Object>();
    private static HashMap<Integer, V8Object> jsInstanceMap = new HashMap<Integer, V8Object>();

    public static V8Object getV8ObjectForObject(V8 runtime, Object o) {
        int hash = System.identityHashCode(o);
        Class clz = o.getClass();
        String clzName = getClassName(clz);
        logger.fine("Finding V8Object for: " + clzName + " : "+ hash + "");

        if (jsInstanceMap.containsKey(hash)) {
            V8Object jsInst = (V8Object) jsInstanceMap.get(hash);
            if (!jsInst.isReleased()) {
                return jsInst.twin();
            }
            logger.warning("Trying to return a released instance!");
        }

        logger.fine("> None found, registering new: " + clzName);

        V8Object res = new V8Object(runtime);
        res.add("__javaInstance", hash);
        res.add("__javaClass", clzName);

        registerInstance(o);
        jsInstanceMap.put(hash, res);

        // NOTE: important to do this here to avoid looking up for "java.lang.Class" endlessly
        if (!clz.equals(Class.class))
        {
            logger.fine("ADDED _class for: " + clzName);
            res.add("__class", getV8ObjectForObject(runtime, clz));
        }
        else
            logger.fine("IGNORED _class for: " + clzName);

        return res.twin();
    }

    public static Object getInstance(int hash) {
        if (!javaInstanceMap.containsKey(hash)) {
            logger.warning("Hash missing: " + hash);
            return null;
        }
        return javaInstanceMap.get(hash);
    }

    public static int registerInstance(Object o) {
        int hash = System.identityHashCode(o);
        if (javaInstanceMap.containsKey(hash)) {
            logger.warning("Hash collision: " + hash);
        }
        javaInstanceMap.put(hash, o);
        return hash;
    }

    public static String getClassName(Class clz) {
        // TODO: find a better way of determining inner classes
        // canonical is null for nested classes
        String canonicalName = clz.getCanonicalName();
        String name = clz.getName();
        if (name.equals(canonicalName)) {
            return canonicalName;
        }
        return name;
    }

    public static Class[] getArrayClasses(Object[] arr) {
        Class[] classes = new Class[arr.length];
        for (int i = 0; i < arr.length; i++) {
            // TODO: proper handling & testing of null params
            if (arr[i] != null)
                classes[i] = arr[i].getClass();
        }
        return classes;
    }
}
