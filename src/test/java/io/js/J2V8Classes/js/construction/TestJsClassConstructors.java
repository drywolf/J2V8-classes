package io.js.J2V8Classes.js.construction;

import io.js.J2V8Classes.*;
import com.eclipsesource.v8.V8;
import com.eclipsesource.v8.V8Object;
import com.eclipsesource.v8.NodeJS;

import java.io.File;


import org.junit.Assert;
import org.junit.Test;

public class TestJsClassConstructors {
    @Test
    public void basicConstructionTest() {
        // V8 v8 = V8.createV8Runtime();

        NodeJS njs = NodeJS.createNodeJS();
        V8 v8 = njs.getRuntime();

        v8.getLocker().acquire();

        V8JavaClasses.injectClassHelper(v8, "TestJsClassConstructors");
        v8.getLocker().acquire();
        v8.getLocker().checkThread();
        System.out.println("Thread: " + Thread.currentThread());
        System.out.println("hasLock: " + v8.getLocker().hasLock());
        System.out.println("lock-owner: " + v8.getLocker().getOwner());
        System.out.println("same: " + (v8.getLocker().getOwner() == Thread.currentThread()));
        
        njs.exec(new File("C:/Users/woste/Documents/code/GitHub/J2V8-classes/src/test/resources/js/construction/TestJsClassConstructors.js"));
        //v8.executeVoidScript(Utils.getScriptSource(this.getClass().getClassLoader(), "./js/construction/TestJsClassConstructors.js"));

        while(njs.isRunning()) {
            njs.handleMessage();
        }

        V8Object b = njs.getRuntime().getObject("b");
        String b_name = b.getString("name");
        Assert.assertEquals("bebe", b_name);

        Assert.assertEquals("bebe", v8.executeStringScript("b.name"));

        Assert.assertEquals(1, v8.executeIntegerScript("base_1.getA()"));
        Assert.assertEquals(2, v8.executeIntegerScript("base_1.getB()"));

        Assert.assertEquals(4, v8.executeIntegerScript("ext_1.getA()"));
        Assert.assertEquals(-1, v8.executeIntegerScript("ext_1.getB()"));
        Assert.assertEquals(3, v8.executeIntegerScript("ext_1.getC()"));

        // Assert.assertEquals("Hello inst_a!", v8.executeStringScript("inst_a.getHelloMessage()"));

        //V8JavaClasses.release("TestJsClassConstructors");
    }
}
