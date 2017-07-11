package io.js.J2V8Classes;

import com.eclipsesource.v8.V8;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by Brown on 4/27/16.
 */
public class TestClasses {
    //@Test
    public void testClasses() {
        V8 v8 = V8JavaClasses.injectClassHelper(V8.createV8Runtime(), "testClasses");
        v8.executeVoidScript(Utils.getScriptSource(this.getClass().getClassLoader(), "testClasses.js"));

        Assert.assertEquals(v8.executeStringScript("person.name"), "joe");
        Assert.assertEquals(v8.executeStringScript("jackie.name"), "jackie");
        Assert.assertEquals(v8.executeBooleanScript("jackie.isAwesome()"), true);

        V8JavaClasses.release("testClasses");
    }
}
