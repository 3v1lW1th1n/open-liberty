/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.wsat.fat;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import componenttest.custom.junit.runner.AlwaysPassesTest;

@RunWith(Suite.class)
@SuiteClasses({
	AlwaysPassesTest.class,
	AssertionTest.class,
	DBTest.class,
	DBTestDisabled.class,
	DBWithoutAssertionTest.class,
	EJBCDITest.class,
	MultiServerTest.class,
	SSLTest.class,
//	EndToEndTest.class,// ?????????????????????????
	
	/**
	 * tWAS migration FAT
	 */
	SimpleTest.class,
	ComplexTest.class,
	SleepTest.class,
	LPSTest.class,
	LPSDisabledTest.class,
	MultiThreadedTest.class
})
public class FATSuite {

}
