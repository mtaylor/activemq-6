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

import java.util.Map;

import org.apache.activemq6.api.core.TransportConfiguration;
import org.apache.activemq6.api.core.client.ClientSession;
import org.apache.activemq6.api.core.client.ClientSessionFactory;
import org.apache.activemq6.api.core.client.HornetQClient;
import org.apache.activemq6.api.core.client.ServerLocator;
import org.apache.activemq6.api.core.management.AcceptorControl;
import org.apache.activemq6.api.core.management.ResourceNames;
import org.apache.activemq6.tests.util.UnitTestCase;

/**
 * A AcceptorControlUsingCoreTest
 *
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 *
 *
 */
public class AcceptorControlUsingCoreTest extends AcceptorControlTest
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // AcceptorControlTest overrides --------------------------------

   private ClientSession session;

   @Override
   protected AcceptorControl createManagementControl(final String name) throws Exception
   {
      ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(UnitTestCase.INVM_CONNECTOR_FACTORY));
      addServerLocator(locator);
      ClientSessionFactory sf = createSessionFactory(locator);
      session = sf.createSession(false, true, true);
      addClientSession(session);
      session.start();

      return new AcceptorControl()
      {

         private final CoreMessagingProxy proxy = new CoreMessagingProxy(session, ResourceNames.CORE_ACCEPTOR + name);

         public String getFactoryClassName()
         {
            return (String)proxy.retrieveAttributeValue("factoryClassName");
         }

         public String getName()
         {
            return (String)proxy.retrieveAttributeValue("name");
         }

         @SuppressWarnings("unchecked")
         public Map<String, Object> getParameters()
         {
            return (Map<String, Object>)proxy.retrieveAttributeValue("parameters");
         }

         public boolean isStarted()
         {
            return (Boolean)proxy.retrieveAttributeValue("started");
         }

         public void start() throws Exception
         {
            proxy.invokeOperation("start");
         }

         public void stop() throws Exception
         {
            proxy.invokeOperation("stop");
         }

      };
   }

   // Public --------------------------------------------------------

   @Override
   @Test
   public void testStartStop() throws Exception
   {
      // this test does not make sense when using core messages:
      // the acceptor must be started to receive the management messages
   }

}
