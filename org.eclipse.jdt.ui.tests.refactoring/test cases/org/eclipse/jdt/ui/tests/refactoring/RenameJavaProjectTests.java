/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.refactoring;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.corext.refactoring.rename.RenameJavaProjectProcessor;
import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;

public class RenameJavaProjectTests extends RefactoringTest {

	private static final Class clazz= RenameJavaProjectTests.class;

	public RenameJavaProjectTests(String name) {
		super(name);
	}

	public static Test suite() {
		return new MySetup(new TestSuite(clazz));
	}

	public void test0() throws Exception {
		IJavaProject p1= null;
		IJavaProject referencing1= null;
		IJavaProject referencing2= null;
		try {
			ParticipantTesting.reset();
			String newProjectName= "newName";
			p1= JavaProjectHelper.createJavaProject("p1", "bin");
			referencing1= JavaProjectHelper.createJavaProject("p2", "bin");
			referencing2= JavaProjectHelper.createJavaProject("p3", "bin");

			JavaProjectHelper.addRTJar(referencing1);
			JavaProjectHelper.addRequiredProject(referencing1, p1);
			JavaProjectHelper.addSourceContainer(referencing1, "src");

			JavaProjectHelper.addRTJar(referencing2);
			JavaProjectHelper.addRequiredProject(referencing2, p1);
			JavaProjectHelper.addSourceContainer(referencing2, "src");

			JavaProjectHelper.addRTJar(p1);

			ParticipantTesting.reset();
			String[] handles= ParticipantTesting.createHandles(p1, p1.getResource());
			RenameJavaProjectProcessor processor= new RenameJavaProjectProcessor(p1);
			RenameRefactoring ref= new RenameRefactoring(processor);
			assertTrue(ref.isAvailable());
			processor.setNewElementName(newProjectName);
			RefactoringStatus result= performRefactoring(ref);
			assertEquals("not expected to fail", null, result);
			assertTrue("p1 is gone", !p1.exists());
			
			ParticipantTesting.testRename(handles, 
				new RenameArguments[] {
					new RenameArguments(newProjectName, true),
					new RenameArguments(newProjectName, true)});
			
			p1= referencing1.getJavaModel().getJavaProject("newName");
			assertTrue("p1 exists", p1.exists());
			
			//check entries in  referencing1
			IClasspathEntry[] entries= referencing1.getRawClasspath();
			assertEquals("expected entries", 3, entries.length);
			for (int i= 0; i < entries.length; i++) {
				IClasspathEntry iClassPathEntry= entries[i];
				if (i == 1) {
					assertEquals("expected entry name", p1.getProject().getFullPath(), iClassPathEntry.getPath());
					assertEquals("expected entry kind", IClasspathEntry.CPE_PROJECT, iClassPathEntry.getEntryKind());
				}
			}

			//check entries in  referencing2
			entries= referencing2.getRawClasspath();
			assertEquals("expected entries", 3, entries.length);
			for (int i= 0; i < entries.length; i++) {
				IClasspathEntry iClassPathEntry= entries[i];
				if (i == 1) {
					assertEquals("expected entry name", p1.getProject().getFullPath(), iClassPathEntry.getPath());
					assertEquals("expected entry kind", IClasspathEntry.CPE_PROJECT, iClassPathEntry.getEntryKind());
				}
			}

		} finally {
			performDummySearch();

			JavaProjectHelper.removeSourceContainer(referencing1, "src");
			JavaProjectHelper.removeSourceContainer(referencing2, "src");

			if (p1 != null && p1.exists()) {
				JavaProjectHelper.delete(p1);
				JavaProjectHelper.delete(referencing1);
				JavaProjectHelper.delete(referencing2);
			}
		}
	}
}
