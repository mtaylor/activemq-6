/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.apache.activemq6.tests.integration.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.activemq6.core.asyncio.impl.AsynchronousFileImpl;
import org.apache.activemq6.core.config.Configuration;
import org.apache.activemq6.core.config.ha.SharedStoreMasterPolicyConfiguration;
import org.apache.activemq6.core.server.HornetQServer;
import org.apache.activemq6.core.server.JournalType;
import org.apache.activemq6.tests.integration.IntegrationTestLogger;
import org.apache.activemq6.tests.logging.AssertionLoggerHandler;
import org.apache.activemq6.tests.util.ServiceTestBase;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

public class FileLockTimeoutTest extends ServiceTestBase
{
   @BeforeClass
   public static void prepareLogger()
   {
      AssertionLoggerHandler.startCapture();
   }

   @AfterClass
   public static void clearLogger()
   {
      AssertionLoggerHandler.stopCapture();
   }

   protected void doTest(final boolean useAIO) throws Exception
   {
      if (useAIO)
      {
         Assert.assertTrue(String.format("libAIO is not loaded on %s %s %s", System.getProperty("os.name"),
                                         System.getProperty("os.arch"), System.getProperty("os.version")),
                           AsynchronousFileImpl.isLoaded()
         );
      }
      Configuration config = super.createDefaultConfig()
         .setHAPolicyConfiguration(new SharedStoreMasterPolicyConfiguration())
         .clearAcceptorConfigurations();

      HornetQServer server1 = createServer(true, config);
      if (useAIO)
      {
         server1.getConfiguration().setJournalType(JournalType.ASYNCIO);
      }
      else
      {
         server1.getConfiguration().setJournalType(JournalType.NIO);
      }
      server1.start();
      server1.waitForActivation(10, TimeUnit.SECONDS);
      final HornetQServer server2 = createServer(true, config);
      if (useAIO)
      {
         server2.getConfiguration().setJournalType(JournalType.ASYNCIO);
      }
      else
      {
         server2.getConfiguration().setJournalType(JournalType.NIO);
      }
      server2.getConfiguration().setJournalLockAcquisitionTimeout(5000);

      // if something happens that causes the timeout to misbehave we don't want the test to hang
      ExecutorService service = Executors.newSingleThreadExecutor();
      Runnable r = new Runnable()
      {
         @Override
         public void run()
         {
            try
            {
               server2.start();
            }
            catch (final Exception e)
            {
               throw new RuntimeException(e);
            }
         }
      };

      Future<?> f = service.submit(r);

      try
      {
         f.get(15, TimeUnit.SECONDS);
      }
      catch (Exception e)
      {
         IntegrationTestLogger.LOGGER.warn("aborting test because server is taking too long to start");
      }

      service.shutdown();

      assertTrue("Expected to find HQ224000", AssertionLoggerHandler.findText("HQ224000"));
      assertTrue("Expected to find \"timed out waiting for lock\"", AssertionLoggerHandler.findText("timed out waiting for lock"));
   }
}