/*
 * Copyright 2017 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.server.logs;

import org.junit.Assert;
import org.junit.Test;

public class LogParamTest {

	@Test
	public void timeTaken() throws InterruptedException {
		LogContext logContext = new LogContext(ctx -> {
		});
		logContext.nanoStartTime = System.nanoTime();
		logContext.nanoEndTime = logContext.nanoStartTime + 1234511111L;
		Assert.assertEquals("1234", LogParam.TIME_TAKEN.supplier.apply(logContext));

		logContext.nanoStartTime = System.nanoTime();
		Thread.sleep(1000);
		logContext.nanoEndTime = System.nanoTime();
		final int time = Integer.valueOf(LogParam.TIME_TAKEN.supplier.apply(logContext));
		Assert.assertTrue(time >= 1000 && time <= 10000);
	}
}
