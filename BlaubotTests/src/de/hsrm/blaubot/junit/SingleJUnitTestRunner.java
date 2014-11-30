package de.hsrm.blaubot.junit;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class SingleJUnitTestRunner {

	public static void main(String[] args) throws ClassNotFoundException {
		String arg = "de.hsrm.blaubot.junit.AllSuite";
		if (args.length > 0) {
			arg = args[0];
		}
		String[] classAndMethod = arg.split("#");
		Request request;
		if (classAndMethod.length == 1) {
			request = Request.classes(Class.forName(classAndMethod[0]));
		} else {
			request = Request.method(Class.forName(classAndMethod[0]), classAndMethod[1]);			
		}
		Result result = new JUnitCore().run(request);
		for (Failure failure : result.getFailures()) {
			System.out.println(failure.getMessage());
		}
		System.exit(result.wasSuccessful() ? 0 : 1);
	}

}
