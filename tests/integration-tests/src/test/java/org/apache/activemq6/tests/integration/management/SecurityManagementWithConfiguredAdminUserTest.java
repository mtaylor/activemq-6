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

import java.util.Set;

import org.apache.activemq6.api.config.HornetQDefaultConfiguration;
import org.apache.activemq6.api.core.TransportConfiguration;
import org.apache.activemq6.core.config.Configuration;
import org.apache.activemq6.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq6.core.security.Role;
import org.apache.activemq6.core.server.HornetQServer;
import org.apache.activemq6.core.server.HornetQServers;
import org.apache.activemq6.core.settings.HierarchicalRepository;
import org.apache.activemq6.spi.core.security.HornetQSecurityManagerImpl;

/**
 * A SecurityManagementTest
 *
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 *
 */
public class SecurityManagementWithConfiguredAdminUserTest extends SecurityManagementTestBase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private final String validAdminUser = "validAdminUser";

   private final String validAdminPassword = "validAdminPassword";

   private final String invalidAdminUser = "invalidAdminUser";

   private final String invalidAdminPassword = "invalidAdminPassword";

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   /**
    *  default CLUSTER_ADMIN_USER must work even when there are other
    *  configured admin users
    */
   @Test
   public void testSendManagementMessageWithClusterAdminUser() throws Exception
   {
      doSendManagementMessage(HornetQDefaultConfiguration.getDefaultClusterUser(), CLUSTER_PASSWORD, true);
   }

   @Test
   public void testSendManagementMessageWithAdminRole() throws Exception
   {
      doSendManagementMessage(validAdminUser, validAdminPassword, true);
   }

   @Test
   public void testSendManagementMessageWithoutAdminRole() throws Exception
   {
      doSendManagementMessage(invalidAdminUser, invalidAdminPassword, false);
   }

   @Test
   public void testSendManagementMessageWithoutUserCredentials() throws Exception
   {
      doSendManagementMessage(null, null, false);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected HornetQServer setupAndStartHornetQServer() throws Exception
   {
      Configuration conf = createBasicConfig()
         .setSecurityEnabled(true)
         .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
      HornetQServer server = addServer(HornetQServers.newHornetQServer(conf, false));
      server.start();
      HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
      HornetQSecurityManagerImpl securityManager = (HornetQSecurityManagerImpl)server.getSecurityManager();
      securityManager.addUser(validAdminUser, validAdminPassword);
      securityManager.addUser(invalidAdminUser, invalidAdminPassword);

      securityManager.addRole(validAdminUser, "admin");
      securityManager.addRole(validAdminUser, "guest");
      securityManager.addRole(invalidAdminUser, "guest");

      Set<Role> adminRole = securityRepository.getMatch(HornetQDefaultConfiguration.getDefaultManagementAddress().toString());
      adminRole.add(new Role("admin", true, true, true, true, true, true, true));
      securityRepository.addMatch(HornetQDefaultConfiguration.getDefaultManagementAddress().toString(), adminRole);
      Set<Role> guestRole = securityRepository.getMatch("*");
      guestRole.add(new Role("guest", true, true, true, true, true, true, false));
      securityRepository.addMatch("*", guestRole);

      return server;
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
