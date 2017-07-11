package io.js.J2V8Classes;

import com.eclipsesource.v8.V8;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Created by emir on 4/28/16.
 */
public class TestJavaArray {

	private static Logger logger = Logger.getLogger("TestJavaArray");

	//@Test
	public void testJavaArrays() {
		V8 v8 = V8JavaClasses.injectClassHelper(V8.createV8Runtime(), "testJavaArray");
		v8.executeVoidScript(Utils.getScriptSource(this.getClass().getClassLoader(), "testJavaArrays.js"));

		logger.info("Testcase-1");
		String[] res1 = (String[]) Utils.v8arrayToObjectArray(v8.executeArrayScript("StaticAnimals.SomeFuncArray(['val1', 'val2'])"));
		String[] a1 = new String[]{"val1", "val2", "newVal"};
		Assert.assertEquals(Arrays.toString(a1), Arrays.toString(res1));

		logger.info("Testcase-2");
		String[] names2 = (String[]) Utils.v8arrayToObjectArray(v8.executeArrayScript("StaticAnimals.SomeFuncVarargs([myBear, StaticAnimals.cat])"));
		String[] a2 = new String[]{"bear", "cat"};
		Assert.assertEquals(Arrays.toString(a2), Arrays.toString(names2));

		logger.info("Testcase-3");
		String[] names3 = (String[]) Utils.v8arrayToObjectArray(v8.executeArrayScript("StaticAnimals.SomeFuncVarargs([myBear, myBear2])"));
		String[] a3 = new String[]{"bear", "bear"};
		Assert.assertEquals(Arrays.toString(a3), Arrays.toString(names3));

		logger.info("Testcase-4");
		Object[] res2 = (Object[]) Utils.v8arrayToObjectArray(v8.executeArrayScript("StaticAnimals.SomeFuncArray([123, 456])"));
		int[] a4 = new int[]{123, 456, 9};
		Assert.assertEquals(Arrays.toString(a4), Arrays.toString(res2));

//		ArrayList listExample = new ArrayList();
//		listExample.add("val1");
//		listExample.add("val2");
//		listExample.add("val3");
//		Assert.assertEquals(listExample.toString(), runtime.executeStringScript("StaticAnimals.SomeFuncWithArrayList(['val1', 'val2', 'val3']);"));

		V8JavaClasses.release("testJavaArray");
	}
}
