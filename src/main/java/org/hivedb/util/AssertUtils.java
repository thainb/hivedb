package org.hivedb.util;

import org.junit.Assert;import static org.junit.Assert.fail;import static org.junit.Assert.assertEquals;
import org.hivedb.util.functional.Filter;
import org.hivedb.util.functional.Toss;
import org.hivedb.util.functional.Transform;

import java.util.Stack;

public class AssertUtils  {
	public static abstract class UndoableToss extends ThrowableUndoable implements Toss
	{
	}
	/**
	 *  Asserts that the implementation of Toss throws an exception. 
	 * @param toss A functor to wrap code that throws an exception
	 */
	public static void assertThrows(Toss toss)
	{
		try {
			toss.f();
		} catch (Exception e) {
			return;
		}
		fail("Expected exception but none occured");
	}
	/**
	 *  Asserts that the implementation of Toss throws an exception of the given type 
	 * @param toss A functor to wrap code that throws an exception
	 */
	public static void assertThrows(Toss toss, Class<? extends Exception> exceptionType)
	{
		try {
			toss.f();
		} catch (Exception e) {
			if (e.getClass().equals(exceptionType)) // Should check inheritance and implements also
				return;
			throw new RuntimeException("Expected exception of type " + exceptionType.getName() + " but got exception of type " + e.getClass().getName(), e);
		}
		fail("Expected exception of type " + exceptionType.getName() + " but no exception occured occured");
	}
	/**
	 *  Asserts that the implementation of UndoablToss throws an exception of the given type, and then calls all the Undo functors created in UndoableToss.
	 *  Calls TestCase.fail() if no exception is thrown
	 *  Throws a RuntimeException if an exception occurs in one of the Undo.f() calls.
	 *  Synopsis:
	 *  final Bar bar = foo.getBar();
	 *  assertThrows(new UndoableToss() { public void f() throws Exception { // calls UndoableToss().f, catches the exception, and then calls all Undo.f()s defined.
	 *  	final String name = foo.getName();
	 *  	new Undo() { public void f() throws Exception { // construct the Undo before the expected exception
	 *  		foo.setName(name);
	 *  	}};
	 *  	foo.setBar(invalid data); // expects a throw
	 *  }});
	 * @param toss A functor to wrap code that throws an exception
	 */
	public static void assertThrows(UndoableToss toss)
	{
		try {
			toss.f();
		} catch (Exception e) {
			try {
				toss.undo();
				return;
			}
			catch (Exception ue) {
				new RuntimeException("Got initial expected exception but got unexpected exception calling undo", ue);
			}
		}
		fail("Expected exception but none occured");
	}
	public static void assertThrows(UndoableToss toss, Class<? extends Exception> exceptionType)
	{
		try {
			toss.f();
		} catch (Exception e) {
			try {
				if (e.getClass().equals(exceptionType)) // Should check inheritance and implements also
					toss.undo();
				else
					throw new RuntimeException("Expected exception of type " + exceptionType.getName() + " but got exception of type " + e.getClass().getName(), e);
				return;
			}
			catch (Exception ue) {
				throw new RuntimeException("Got initial expected exception but got unexpected exception calling undo", ue);
			}
		}
		fail("Expected exception but none occured");
	}
	/**
	 *  Asserts that the implementation of Toss does not throw an exception.
	 * @param toss A functor to wrap code that throws an exception
	 */
	public static void assertDoesNotThrow(Toss toss)
	{
		try {
			toss.f();
		} catch (Exception e) {
			fail("Got unexpected exception: " + e.getMessage() + e.getStackTrace());
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void assertUnique(Iterable i) {
		assertEquals(Transform.toCollection(i).size(), Filter.getUnique(i).size());
	}
	
	private static abstract class ThrowableUndoable {
		public abstract void f() throws Exception;
		public void undo() throws Exception
		{
			while (undoStack.size() != 0)
				undoStack.pop().f();
		}
		public void cycle() throws Exception
		{
			f();
			undo();
		}
		Stack<Undo> undoStack = new Stack<Undo>();
		public abstract class Undo
		{
			public Undo()
			{
				undoStack.push(this);
			}
			public abstract void f() throws Exception;
		}
	}
	
	public static void assertImplements(Class expected, Object actual) {
		Class[] implemented = actual.getClass().getInterfaces();
		boolean implementsInterface = false;
		for(Class inter : implemented)
			implementsInterface |= expected.equals(inter);
		Assert.assertTrue("Object did not implement " + expected.toString(), implementsInterface);
	}
}
