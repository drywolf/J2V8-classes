package io.js.J2V8Classes;

import java.io.File;
import java.lang.ClassLoader;
import java.net.URLClassLoader;
import java.net.URL;

import com.eclipsesource.v8.*;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Brown on 4/26/16.
 */
public class V8JavaClasses {

    private static HashMap<String, V8> runtimes = new HashMap<>();

    private static Logger logger = Logger.getLogger("V8JavaClasses");

    public static V8 getRuntime(String name) {
        return runtimes.get(name);
    }

    @Deprecated
    public static void initMainClassPaths()
    {
        String classpath = System.getProperty("java.class.path");
        logger.fine("Initializing from main class-path: " + classpath);

        String[] classpathEntries = classpath.split(File.pathSeparator);

        ClassPool cp = ClassPool.getDefault();

        for (String cpEntry : classpathEntries)
        {
            //Class c = Class.forName(superName);
            try {
                logger.fine("Adding main class-path: " + cpEntry);
                cp.insertClassPath(cpEntry);
                logger.fine("Added main class-path: " + cpEntry);
            }
            catch (NotFoundException e) {
                logger.warning("Main class-path error: " + e);
            }
        }
    }

    public static void addtoClassPath(Class c)
    {
        logger.fine("addtoClassPath: " + c);

        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(c));
    }

    public static String classAliasFor(String originalClass)
    {
        String alias = ClassAliases.get(originalClass);

        if (alias != null)
        {
            logger.fine("Using class alias: " + alias);
            return alias;
        }

        return originalClass;
    }

    public static HashMap<String, String> ClassAliases = new HashMap<String, String>();

    public static V8 injectClassHelper(V8 runtime, String runtimeName) {
        if (runtimes.containsKey(runtimeName)) {
            return runtime;
        }
        runtimes.put(runtimeName, runtime);

        runtime.executeVoidScript("__runtimeName='" + runtimeName + "';");

        runtime.executeVoidScript(
                Utils.getScriptSource(
                        V8JavaClasses.class.getClassLoader(),
                        "abitbol/dist/abitbol.js"
                )
        );

        runtime.executeVoidScript(
                Utils.getScriptSource(
                        V8JavaClasses.class.getClassLoader(),
                        "jsClassHelper.js"
                )
        );


        JavaVoidCallback print = new JavaVoidCallback() {
            public void invoke(final V8Object receiver, final V8Array parameters) {
                StringBuilder sb = new StringBuilder();
                sb.append("JS: ");
                for (int i = 0, j = parameters.length(); i < j; i++) {
                    Object obj = parameters.get(i);
                    sb.append(obj);
//                    if (i < j - 1) {
//                        sb.append(' ');
//                    }
                    if (obj instanceof V8Value) {
                        ((V8Value) obj).release();
                    }
                }
                System.out.println(sb.toString());
            }
        };
        runtime.registerJavaMethod(print, "print");

        JavaVoidCallback log = new JavaVoidCallback() {
            public void invoke(final V8Object receiver, final V8Array parameters) {
                StringBuilder sb = new StringBuilder();
                sb.append("JS: ");
                for (int i = 0, j = parameters.length(); i < j; i++) {
                    Object obj = parameters.get(i);
                    sb.append(obj);
//                    if (i < j - 1) {
//                        sb.append(' ');
//                    }
                    if (obj instanceof V8Value) {
                        ((V8Value) obj).release();
                    }
                }
                logger.fine("JS: " + sb.toString());
            }
        };
        runtime.registerJavaMethod(log, "log");


        JavaVoidCallback getClass = new JavaVoidCallback() {
            public void invoke(final V8Object receiver, final V8Array parameters) {
                String className = parameters.getString(0);
                logger.fine("Getting class: " + className);
                try {
                    getClassInfo(runtime, className, parameters.getObject(1));
                } catch (ClassNotFoundException e) {
                    logger.warning("> getClass > Class not found");
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    logger.warning("> getClass > illegal access");
                    e.printStackTrace();
                }
            }
        };
        runtime.registerJavaMethod(getClass, "JavaGetClass");

        JavaCallback createInstance = new JavaCallback() {
            public V8Object invoke(final V8Object receiver, final V8Array parameters) {
                logger.fine("BEGIN JavaCreateInstance");
                String className = parameters.getString(0);
                logger.fine("BEFORE JavaCreateInstance");
                try {
                    return createInstance(runtime, className, Utils.v8arrayToObjectArray(parameters, 1));
                } catch (ClassNotFoundException e) {
                    logger.warning("> createInstance > Class not found");
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        runtime.registerJavaMethod(createInstance, "JavaCreateInstance");

        JavaCallback generateClass = new JavaCallback() {
            public V8Object invoke(final V8Object receiver, final V8Array parameters) {
                String className = parameters.getString(0);
                String superName = parameters.getString(1);
                V8Array methods = parameters.getArray(2);
                logger.fine("Generating class: " + className + " extending " + superName + " (method count " + methods.length() + ")");

                // CtClass superClz = null;
                
                try {
                    ClassPool cp = ClassPool.getDefault();

                    Class c = Class.forName(superName);
                    cp.insertClassPath(new ClassClassPath(c));
                    logger.fine("Inserted CP for " + superName);

                    // NOTE: disabled until clear if needed
                    //superClz = cp.getCtClass(superName);

                    //if (superClz == null)
                    //    throw new RuntimeException("Not sure if unexpected ... superClz == null");
                    /*
                    Javassist API docs
                    Returns a CtClass object with the given name. This is almost equivalent to get(String) except that classname can be an array-type "descriptor"
                    (an encoded type name) such as [Ljava/lang/Object;. Using this method is not recommended; this method should be used only to obtain the
                    CtClass object with a name returned from getClassInfo in javassist.bytecode.ClassPool. getClassInfo returns a fully-qualified class name but,
                    if the class is an array type, it returns a descriptor.
                    */
                    //superClz = cp.get(superName);
                }
                catch (Exception e) {
                    logger.warning("Unable to automatically update Class-Path: " + e);
                }

                // NOTE: disabled until clear if needed
                // CtClass clz = cp.makeClass(className, superClz);
                // boolean isiface = superClz.isInterface();

                // // add interface if needed
                // if (superClz.isInterface()) {
                    
                //     clz = cp.makeClass(className);
                //     clz.addInterface(superClz);
                // }

                ClassGenerator.createClass(runtimeName, className, superName, methods);

                methods.release();
                return new V8Object(runtime);
            }
        };
        runtime.registerJavaMethod(generateClass, "JavaGenerateClass");
        return runtime;
    }

    private static void getClassInfo(V8 runtime, String className, V8Object classInfo) throws ClassNotFoundException, IllegalAccessException {
        logger.fine("Getting class info: " + className);

        className = classAliasFor(className);

        Class initclz = null;

        try {
        logger.fine("Getting Java class: " + className);

        // try {
            initclz = Class.forName(className);
        // }
        // catch (ClassNotFoundException e)
        // {
        // }

        if (initclz == null)
        {
            logger.fine("Class not found in default class-loader");

            // TODO: find the ClassLoader that contains the class
            // for (ClassLoader cl : cls)
            // {
            //     logger.fine("Looking into class-loader: " + cl.toString());

            //     initclz = Class.forName(className, true, cl);

            //     if (initclz != null)
            //     {
            //         logger.fine("Class found at alternative class-loader");
            //         break;
            //     }
            // }
        }

        Class clz = initclz;

        logger.fine("After getting Java class: " + className);

        generateAllGetSet(classInfo.getObject("statics"), clz, clz, true);
        generateAllGetSet(classInfo.getObject("publics"), clz, clz, false);
        
        String clzName = Utils.getClassName(clz);
        classInfo.add("__javaClass", clzName);

        Class superClz = clz.getSuperclass();
        if (superClz != Object.class && superClz != null) {
            String superClzName = Utils.getClassName(superClz);
            logger.fine("__javaSuperclass[c] = " + superClzName);
            classInfo.add("__javaSuperclass", superClzName);
        }
        // else {
        //     Class[] interfaces = clz.getInterfaces();

        //     // TODO: support multiple interface inheritance in JS
        //     if (interfaces != null && interfaces.length > 0) {
        //         Class superInterface = interfaces[0];
        //         String superInterfaceName = Utils.getClassName(superInterface);
        //         logger.fine("__javaSuperclass[i] = " + superInterfaceName);
        //         logger.fine("__javaSuperclass = " + superInterfaceName);
        //     }
        // }

        if (className.equals("java.lang.Class"))
        {
            logger.fine("Skipping __class for java.lang.Class");
            return;
        }

        V8Object statics = classInfo.getObject("statics");
        V8Object jsF = statics.getObject("fields");

        JavaCallback getter = new JavaCallback() {
            public V8Object invoke(final V8Object receiver, final V8Array parameters) {
                try {
                    logger.fine("get __class");
                    return Utils.toV8Object(runtime, clz);
                } catch (V8ResultUndefined e) {
                    e.printStackTrace();
                    logger.fine("ERROR getting __class " + e);
                }
                return new V8Object(runtime);
            }
        };
        jsF.registerJavaMethod(getter, "__get_" + "__class");

        JavaVoidCallback setter = new JavaVoidCallback() {
            public void invoke(final V8Object receiver, final V8Array parameters) {
                logger.fine("ERROR not allowed to set __class");
            }
        };
        jsF.registerJavaMethod(setter, "__set_" + "__class");
        }
        catch (ClassNotFoundException e) {
            logger.warning("Empty classInfo for: " + className + " because class was not found");
        }
        catch (RuntimeException e) {
            logger.warning("Invalid JS proxy class because RT: " + e.toString());
            e.printStackTrace();
            throw e;
        }
        catch (Exception e) {
            logger.warning("Invalid JS proxy class because: " + e.toString());
            e.printStackTrace();
            throw e;
        }
        // V8 runtime = classInfo.getRuntime();
        // V8Object jscls = Utils.getV8ObjectForObject(runtime, clz);
        // classInfo.add("__class", jscls);
    }

    private static V8Object createInstance(V8 runtime, String className, Object[] parameters) throws ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        logger.fine("Getting class instance: " + className);
        Class clz = Class.forName(className);

        // TODO: support for nested classes? http://stackoverflow.com/a/17485341
        List<String> paramTypes = new ArrayList<String>();
        for (Object o : parameters) {
            paramTypes.add(o.getClass().toString());
        }

        logger.fine("> Getting constructor for args: " + String.join(", ", paramTypes));
        Executable inferredMethod = Utils.findMatchingExecutable(
                clz.getConstructors(),
                parameters,
                null
        );

        if (inferredMethod == null) {
            logger.warning("> Could not find constructor for args " + Utils.printArray(parameters));
            return null;
        }

        Object instance = ((Constructor) inferredMethod).newInstance(parameters);
        return Utils.getV8ObjectForObject(runtime, instance);
    }

    private static void generateAllGetSet(V8Object parent, Class clz, Object instance, boolean statics) {
        V8 runtime = parent.getRuntime();

        logger.fine("Generating getters and setters for: " + clz.getName() + "(" + System.identityHashCode(instance) + ", " + statics + ")");

        logger.fine("> Getting fields");
        Field[] f = clz.getDeclaredFields();
        V8Object jsF = parent.getObject("fields");
        for (int i = 0; i < f.length; i++) {
            if (Modifier.isStatic(f[i].getModifiers()) == statics) {
                generateGetSet(jsF, f[i]);
            }
        }

        logger.fine("> Getting methods");
        // Dont send in js methods??
        String[] jsMethods;
        try {
            Field __jsMethods = clz.getField("__jsMethods");
            jsMethods = (String[]) __jsMethods.get(clz);
        } catch(NoSuchFieldException e) {
            jsMethods = new String[]{};
        } catch(IllegalAccessException e) {
            jsMethods = new String[]{};
        }
        logger.fine(">> jsMethods= " + String.join(",", jsMethods));

        Method[] methods = clz.getDeclaredMethods();
        V8Object jsM = parent.getObject("methods");
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (Modifier.isStatic(m.getModifiers()) == statics) {
                generateMethod(jsM, m);
            }
        }

        Class[] interfaces = clz.getInterfaces();

        if (interfaces != null) {
            for (Class iface : interfaces) {
                logger.fine(">> jsMethods[" + iface + "]= " + String.join(",", jsMethods));
                
                Method[] imethods = iface.getDeclaredMethods();

                for (int i = 0; i < imethods.length; i++) {
                    Method m = imethods[i];
                    if (Modifier.isStatic(m.getModifiers()) == statics) {
                        generateMethod(jsM, m);
                    }
                }
            }
        }

        if (!statics) {
            Class superClz = clz.getSuperclass();
            if (superClz != Object.class && superClz != null) {
                logger.fine("> Adding super object for: " + superClz.getName());
                V8Object superData = runtime.executeObjectScript("ClassHelpers.getBlankClassInfo()");
                superData.add("__javaClass", superClz.getCanonicalName());
                generateAllGetSet(superData.getObject("publics"), superClz, instance, false);
                parent.add("superData", superData);
            }
        }
    }

    private static void generateMethod(V8Object parent, Method m) {
        V8 runtime = parent.getRuntime();

        String mName = m.getName();
        logger.fine(">> M: " + mName);

        int mods = m.getModifiers();
        if (Modifier.isPrivate(mods)) {
            logger.fine(">>> Skipping private");
            return;
        }
        if (Modifier.isProtected(mods)) {
            logger.fine(">>> Skipping protected");
            return;
        }

        JavaCallback staticMethod = new JavaCallback() {
            public V8Object invoke(final V8Object receiver, final V8Array parameters) {
                try {
                    Object fromRecv = getReceiverFromCallback(receiver, "<method> " + mName);
                    if (fromRecv == null) {
                        logger.warning("Callback with no bound java receiver! (" + mName + ")");
                        return new V8Object(runtime);
                    }
                    Object[] args = Utils.v8arrayToObjectArray(parameters);
                    logger.fine("Method: " + mName);
                    logger.fine("Args: " + Utils.printArray(args));

                    Class fromRecvClz = fromRecv instanceof Class ? (Class) fromRecv : fromRecv.getClass();

                    logger.fine("fromRecvClz: " + Utils.getClassName(fromRecvClz));

                    Executable inferredMethod = Utils.findMatchingExecutable(
                            fromRecvClz.getMethods(),
                            args,
                            mName
                    );

                    if (inferredMethod == null) {
                        return new V8Object(runtime);
                    }

                    inferredMethod.setAccessible(true);
                    Object v = ((Method) inferredMethod).invoke(fromRecv, Utils.matchExecutableParams(inferredMethod, args));
                    logger.fine("Method returned: " + v);
                    return Utils.toV8Object(runtime, v);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return new V8Object(runtime);
            }
        };
        parent.registerJavaMethod(staticMethod, mName);
    }

    public static Object getReceiverFromCallback(V8Object receiver, String target) throws ClassNotFoundException {
        if (!receiver.contains("__javaInstance")) {
            if (!receiver.contains("__javaClass")) {
                logger.warning("Java receiver is missing class & instance meta-data! (target: " + target + ")");
                return null;
            }
            return Class.forName(receiver.getString("__javaClass"));
        }
        return Utils.getInstance(receiver.getInteger("__javaInstance"));
    }

    private static V8Object getFromField(V8 runtime, V8Object receiver, Field f) throws IllegalAccessException, ClassNotFoundException {
        Object fromRecv = getReceiverFromCallback(receiver, "<field> " + f.getName());
        if (fromRecv == null) {
            logger.warning("Could not find receiving Object for callback! " + f);
            return new V8Object(runtime);
        }
        f.setAccessible(true);
        Object v = f.get(fromRecv);
        return Utils.toV8Object(runtime, v);
    }

    private static void generateGetSet(V8Object parent, Field f) {
        V8 runtime = parent.getRuntime();

        String fName = f.getName();
        logger.fine(">> F: " + fName);

        int mods = f.getModifiers();
        if (Modifier.isPrivate(mods)) {
            logger.fine(">>> Skipping private");
            return;
        }
        if (Modifier.isProtected(mods)) {
            logger.fine(">>> Skipping protected");
            return;
        }

        JavaCallback getter = new JavaCallback() {
            public V8Object invoke(final V8Object receiver, final V8Array parameters) {
                try {
                    return getFromField(runtime, receiver, f);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (V8ResultUndefined e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return new V8Object(runtime);
            }
        };
        parent.registerJavaMethod(getter, "__get_" + fName);

        JavaVoidCallback setter = new JavaVoidCallback() {
            public void invoke(final V8Object receiver, final V8Array parameters) {
                try {
                    Object fromRecv = getReceiverFromCallback(receiver, "<setter> " + fName);

                    if (fromRecv == null) {
                        logger.warning("Could not find receiving Object for callback! (" + fName + ")");
                        return;
                    }

                    Object v = parameters.get(0);
                    if (v.getClass() == V8Object.class) {
                        V8Object jsObj = (V8Object) v;
                        Object javaObj = getReceiverFromCallback(jsObj, "<set-value> " + fName + " = " + v);
                        if(javaObj == null){
                            return;
                        }
                        v = javaObj;
                    }

                    f.set(fromRecv, v);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        };
        parent.registerJavaMethod(setter, "__set_" + fName);
    }

    public static void release(String runtimeName) {
        Utils.releaseAllFor(runtimes.get(runtimeName));
        // TODO: better release logic... maybe add some cleanup stuff to jsClassHelper
        V8 v8 = runtimes.get(runtimeName);
        v8.release(false);
        runtimes.remove(runtimeName);
    }
}
