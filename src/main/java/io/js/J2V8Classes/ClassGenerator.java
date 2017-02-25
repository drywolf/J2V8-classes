package io.js.J2V8Classes;

import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Array;
import com.eclipsesource.v8.V8Object;
import javassist.*;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.logging.Logger;

/**
 * Created by Brown on 4/27/16.
 */
public class ClassGenerator {
    private static Logger logger = Logger.getLogger("ClassGenerator");

    public static Class createClass(String runtimeName, String canonicalName, String superClzName, V8Array methods) {
        logger.info("Generating class: " + canonicalName + " extends " + superClzName);
        ClassPool cp = ClassPool.getDefault();

        CtClass clz = null;

        try {
            CtClass superClz = cp.getCtClass(superClzName);
            clz = cp.makeClass(canonicalName, superClz);

            if (superClz.isInterface()) {
                clz = cp.makeClass(canonicalName);
                clz.addInterface(superClz);
                logger.info("Implementing interface: " + superClzName);
            }

            CtField runtimeNameField = new CtField(cp.get("java.lang.String"), "runtimeName", clz);
            runtimeNameField.setModifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL);
            clz.addField(runtimeNameField, CtField.Initializer.constant(runtimeName));

            ClassPool.getDefault().insertClassPath(new ClassClassPath(V8.class));
            cp.importPackage("com.eclipsesource.v8");
            cp.importPackage("io.js.J2V8Classes");

            CtMethod runJsFunc = CtNewMethod.make(
                    "private Object runJsFunc(String name, Object[] args) { " +
                        "java.util.logging.Logger logger = java.util.logging.Logger.getLogger(\"runJsFunc\");" +
                        "logger.info(\"runJsFunc: \" + name + \" BEGIN\");" +
                        "try {" +
                        "com.eclipsesource.v8.V8 v8 = io.js.J2V8Classes.V8JavaClasses.getRuntime(runtimeName);" +
                        "com.eclipsesource.v8.V8Locker lockr = v8.getLocker();"+
                        "lockr.acquire();"+
                        "com.eclipsesource.v8.V8Array v8Args = new com.eclipsesource.v8.V8Array(v8);" +
                        "v8Args.push(System.identityHashCode(this));" +
                        "v8Args.push(name);" +
                        "v8Args.push(io.js.J2V8Classes.Utils.toV8Object(v8, args));" +
                        "Object res = v8.executeFunction(\"executeInstanceMethod\", v8Args);" +
                        "com.eclipsesource.v8.V8Object v8res = (res instanceof com.eclipsesource.v8.V8Object ? (com.eclipsesource.v8.V8Object)res : null);" +
                        "if (v8res != null && !v8res.equals(com.eclipsesource.v8.V8.getUndefined())) {" +
                            "int __javaInstance = v8res.getInteger(\"__javaInstance\");" +
                            "res = io.js.J2V8Classes.Utils.getInstance(__javaInstance);" +
                        "}" +
                        "v8Args.release();" +
                        "lockr.release();"+
                        "logger.info(\"runJsFunc: \" + name + \" END\");" +
                        "return res;" +
                        "} catch (RuntimeException e) {" +
                            "logger.info(\"RT-Error in runJsFunc: \" + name + \": \" + e.toString());" +
                            "e.printStackTrace();" +
                        "} catch (Exception e) {" +
                            "logger.info(\"Error in runJsFunc: \" + name + \": \" + e.toString());" +
                            "e.printStackTrace();" +
                        "}" +
                        "logger.info(\"runJsFunc: \" + name + \" END null\");" +
                        "return null;" +
                    "}",
                    clz
            );
            clz.addMethod(runJsFunc);

            CtMethod createJsInstance = CtNewMethod.make(
                    "private void createJsInstance(String classname, Object[] args) { " +
                        "java.util.logging.Logger logger = java.util.logging.Logger.getLogger(\"createJsInstance\");" +
                        "logger.info(\"createJsInstance: \" + classname + \" BEGIN\");" +
                        "try {" +
                        "com.eclipsesource.v8.V8 v8 = io.js.J2V8Classes.V8JavaClasses.getRuntime(runtimeName);" +
                        "com.eclipsesource.v8.V8Locker lockr = v8.getLocker();"+
                        "lockr.acquire();"+
                        "com.eclipsesource.v8.V8Array v8Args = new com.eclipsesource.v8.V8Array(v8);" +
                        "com.eclipsesource.v8.V8Object inst = io.js.J2V8Classes.Utils.toV8Object(v8, this);" +
                        "v8Args.push(inst);" +
                        "Object res = v8.executeFunction(\"createJsInstance\", v8Args);" +
                        "v8Args.release();" +
                        "lockr.release();"+
                        "} catch (RuntimeException e) {" +
                            "logger.info(\"RT-Error in createJsInstance: \" + classname + \": \" + e.getMessage());" +
                        "} catch (Exception e) {" +
                            "logger.info(\"Error in createJsInstance: \" + classname + \": \" + e.getMessage());" +
                        "}" +
                        "logger.info(\"createJsInstance: \" + classname + \" END\");" +
                    "}",
                    clz
            );
            clz.addMethod(createJsInstance);

            // Add matching constructors if the super class is not dynamic
            CtConstructor[] c = superClz.getConstructors();

            if (c.length != 0) {
                logger.info("Constructors found " + c.length);

                for (int i = 0; i < c.length; i++) {

                    CtConstructor proxyConstructor = CtNewConstructor.make(
                        c[i].getParameterTypes(),
                        c[i].getExceptionTypes(),
                        "{" +
                            "Object[] args = null;" +
                            "this.createJsInstance(\"" + clz.getName() + "\", args);" +
                        "}",
                        clz
                    );
                    clz.addConstructor(proxyConstructor);
                }
            } 
            else {
                logger.info("No constructors for " + clz.getSimpleName() + ", using default ctor");

                CtConstructor proxyConstructor = CtNewConstructor.make(
                    "public " + clz.getSimpleName() + "()" +
                    "{" +
                        //"super();" +
                        "Object[] args = null;" +
                        "this.createJsInstance(\"" + clz.getName() + "\", args);" +
                    "}",
                    clz
                );
                clz.addConstructor(proxyConstructor);
            }

            String defaultReturn = "Object";
            String defaultArgs = "Object[] args";

            String methodNames = "";
            for (int i = 0, j = methods.length(); i < j; i++) {
                V8Object v8o = methods.getObject(i);
                String name = v8o.getString("name");
                logger.info("> Adding method: " + name);
                methodNames += "\"" + name + "\"";

                V8Object annotations = v8o.getObject("annotations");
                if (annotations.contains("Override") && annotations.getBoolean("Override")) {
                    logger.info(">> is override");
                    CtMethod superMethod = findSuperMethod(superClz, name);

//                    logger.info(">> >> " + Arrays.toString(superMethod.getParameterTypes()));
//                    logger.info(">> >> " + superMethod.getReturnType().getName());

                    String superReturnType = superMethod.getReturnType().getName();
                    CtClass[] superArgs = superMethod.getParameterTypes();
                    String argsString = "";
                    String jsFuncArgString = "";
                    for (int k = 0; k < superArgs.length; k++) {
                        argsString += superArgs[k].getName() + " a" + k;
                        jsFuncArgString += "a" + k;
                        if (k < superArgs.length - 1) {
                            argsString += ",";
                            jsFuncArgString += ",";
                        }
                    }

                    CtClass genType = superMethod.getReturnType().getComponentType();

                    logger.info(">> RETURN " + superReturnType);
                    logger.info(">> ARGS " + argsString);
                    logger.info(">> JSARGS " + jsFuncArgString);

                    String meth = "public " + superReturnType + " " + name + "(" + argsString + ") { ";
                    if (jsFuncArgString.length() > 0) {
                        meth += "Object[] args = new Object[]{" + jsFuncArgString + "};";
                    } else {
                        meth += "Object[] args = null;";
                    }
                    if (superReturnType.equals("void")) {
                        meth += "this.runJsFunc(\"" + name + "\", args);" + "}";
                    } else {
                        String unboxedReturnType = Utils.UnboxReturnType(superReturnType);
                        // TODO: refactor in a single line form if possible inside Utils method
                        // return Utils.unboxReturn(this.runJsFunc(\"" + name + "\", args));
                        // ((unboxedReturnType)$arg0).unboxSuffix();
                        // e.g. ((Boolean)$arg0).booleanValue();
                        // or   ((Object)$arg0);
                        meth += unboxedReturnType + " result = (" + unboxedReturnType + ") this.runJsFunc(\"" + name + "\", args);";
                        meth += "return result" + Utils.UnboxReturnValue(superReturnType) + ";";
                        meth += "}";
                    }

                    // JAVA 5 proper Bytecode example
                    // Boolean var3 = (Boolean)this.runJsFunc("shouldExecuteOnProject", var2);
                    // return var3.booleanValue();

                    logger.info(">> Full-Signature: " + meth);

                    CtMethod proxyMethod = CtNewMethod.make(
                            meth,
                            clz
                    );

                    if (!superClz.isInterface()) {
                        logger.info("Inherit Super-Method modifiers (not abstract)");
                        proxyMethod.setModifiers(superMethod.getModifiers() & ~Modifier.ABSTRACT);
                    }

                    logger.info("Super-Method modifiers: " + superMethod.getModifiers());
                    logger.info("Proxy-Method modifiers: " + proxyMethod.getModifiers());

                    clz.addMethod(proxyMethod);
                } else {
                    CtMethod proxyMethod = CtNewMethod.make(
                            "public " + defaultReturn + " " + name + "(" + defaultArgs + ") { " +
                                    "return runJsFunc(\"" + name + "\", args);" +
                                    "}",
                            clz
                    );
                    proxyMethod.setModifiers(proxyMethod.getModifiers() | Modifier.VARARGS);
                    clz.addMethod(proxyMethod);
                }
            }

            CtField jsMethods = new CtField(cp.get("[Ljava.lang.String;"), "__jsMethods", clz);
            jsMethods.setModifiers(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL);
            if (methodNames.length() > 0) {
                clz.addField(jsMethods, CtField.Initializer.byExpr("new String[]{" + methodNames + "}"));
            } else {
                clz.addField(jsMethods, CtField.Initializer.byExpr("null"));
            }

            // TODO: get modifiers by annotations from JS ???
            clz.setModifiers(Modifier.PUBLIC);

            logger.info("Class-Modifiers: " + clz.getModifiers());
            logger.info("Full-Class: " + clz.toString());

            try {
                DataOutputStream out = new DataOutputStream(new FileOutputStream(canonicalName + ".class"));
                clz.getClassFile().write(out);
            } catch (Exception e)
            {
                logger.info("Class-Source Error: " + e);
                e.printStackTrace();
            }

            Class result = clz.toClass();

            logger.info("Before-Assert Class");

            try {
                Class assrt = Class.forName(canonicalName);
                logger.info("After-Assert Class: " + assrt);
            } catch (Exception e) {
                logger.info("Class-Assert Error: " + e);
                e.printStackTrace();
            }

            return result;
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }
        catch (NotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static CtMethod findSuperMethod(CtClass clz, String name) {
        logger.info("Finding method: " + name + " on " + clz.getName());
        if (clz == null) {
            return null;
        }

        CtMethod[] methods = clz.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            CtMethod m = methods[i];
            if (m.getName().equals(name)) {
                return m;
            }
        }

        CtClass[] interfaces = null;
        
        try {
            interfaces = clz.getInterfaces();
        } catch (NotFoundException e) {
        }

        if (interfaces != null) {
            for (CtClass iface : interfaces) {
                CtMethod iface_method = findSuperMethod(iface, name);

                if (iface_method != null)
                    return iface_method;
            }
        }

        try {
            return findSuperMethod(clz.getSuperclass(), name);
        } catch (NotFoundException e) {
            return null;
        }
    }
}
