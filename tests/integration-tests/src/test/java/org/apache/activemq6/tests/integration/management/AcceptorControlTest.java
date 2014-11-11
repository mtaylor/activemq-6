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
package org.apache.activemq6.tests.integration.management;

import org.junit.Test;

import java.util.HashMap;

import org.junit.Assert;
import org.apache.activemq6.api.core.HornetQException;
import org.apache.activemq6.api.core.SimpleString;
import org.apache.activemq6.api.core.TransportConfiguration;
import org.apache.activemq6.api.core.client.ClientSession;
import org.apache.activemq6.api.core.client.ClientSessionFactory;
import org.apache.activemq6.api.core.client.ServerLocator;
import org.apache.activemq6.api.core.management.AcceptorControl;
import org.apache.activemq6.api.core.management.CoreNotificationType;
import org.apache.activemq6.core.config.Configuration;
import org.apache.activemq6.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq6.core.server.HornetQServer;
import org.apache.activemq6.core.server.management.Notification;
import org.apache.activemq6.tests.integration.SimpleNotificationService;
import org.apache.activemq6.tests.util.RandomUtil;

/**
 * A AcceptorControlTest
 *
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 *
 * Created 11 dec. 2008 17:38:58
 *
 *
 */
public class AcceptorControlTest extends ManagementTestBase
{
   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   @Test
   public void testAttributes() throws Exception
   {
      TransportConfiguration acceptorConfig = new TransportConfiguration(InVMAcceptorFactory.class.getName(),
                                                                         new HashMap<String, Object>(),
                                                                         RandomUtil.randomString());

      Configuration conf = createBasicConfig()
         .addAcceptorConfiguration(acceptorConfig);
      HornetQServer service = createServer(false, conf, mbeanServer);
      service.start();

      AcceptorControl acceptorControl = createManagementControl(acceptorConfig.getName());

      Assert.assertEquals(acceptorConfig.getName(), acceptorControl.getName());
      Assert.assertEquals(acceptorConfig.getFactoryClassName(), acceptorControl.getFactoryClassName());
   }

   @Test
   public void testStartStop() throws Exception
   {
      TransportConfiguration acceptorConfig = new TransportConfiguration(InVMAcceptorFactory.class.getName(),
                                                                         new HashMap<String, Object>(),
                                                                         RandomUtil.randomString());
      Configuration conf = createBasicConfig()
         .addAcceptorConfiguration(acceptorConfig);
      HornetQServer service = createServer(false, conf, mbeanServer);
      service.start();

      AcceptorControl acceptorControl = createManagementControl(acceptorConfig.getName());

      // started by the server
      Assert.assertTrue(acceptorControl.isStarted());
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory sf = createSessionFactory(locator);
      ClientSession session = sf.createSession(false, true, true);
      Assert.assertNotNull(session);
      session.close();

      acceptorControl.stop();

      Assert.assertFalse(acceptorControl.isStarted());

      try
      {
         sf.createSession(false, true, true);
         Assert.fail("acceptor must not accept connections when stopped accepting");
      }
      catch (HornetQException e)
      {
      }

      acceptorControl.start();

      Assert.assertTrue(acceptorControl.isStarted());

      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);
      Assert.assertNotNull(session);
      session.close();

      acceptorControl.stop();

      Assert.assertFalse(acceptorControl.isStarted());

      try
      {
         sf.createSession(false, true, true);
         Assert.fail("acceptor must not accept connections when stopped accepting");
      }
      catch (HornetQException e)
      {
      }

   }

   @Test
   public void testNotifications() throws Exception
   {
      TransportConfiguration acceptorConfig = new TransportConfiguration(InVMAcceptorFactory.class.getName(),
                                                                         new HashMap<String, Object>(),
                                                                         RandomUtil.randomString());
      Configuration conf = createBasicConfig()
         .addAcceptorConfiguration(acceptorConfig);
      HornetQServer service = createServer(false, conf, mbeanServer);
      service.start();

      AcceptorControl acceptorControl = createManagementControl(acceptorConfig.getName());

      SimpleNotificationService.Listener notifListener = new SimpleNotificationService.Listener();

      service.getManagementService().addNotificationListener(notifListener);

      Assert.assertEquals(0, notifListener.getNotifications().size());

      acceptorControl.stop();

      Assert.assertEquals(1, notifListener.getNotifications().size());
      Notification notif = notifListener.getNotifications().get(0);
      Assert.assertEquals(CoreNotificationType.ACCEPTOR_STOPPED, notif.getType());
      Assert.assertEquals(InVMAcceptorFactory.class.getName(),
                          notif.getProperties().getSimpleStringProperty(new SimpleString("factory")).toString());

      acceptorControl.start();

      Assert.assertEquals(2, notifListener.getNotifications().size());
      notif = notifListener.getNotifications().get(1);
      Assert.assertEquals(CoreNotificationType.ACCEPTOR_STARTED, notif.getType());
      Assert.assertEquals(InVMAcceptorFactory.class.getName(),
                          notif.getProperties().getSimpleStringProperty(new SimpleString("factory")).toString());
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected AcceptorControl createManagementControl(final String name) throws Exception
   {
      return ManagementControlHelper.createAcceptorControl(name, mbeanServer);
   }
}
