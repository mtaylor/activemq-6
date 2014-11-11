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
package org.apache.activemq6.jms.server.config.impl;

import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;

import org.apache.activemq6.api.config.HornetQDefaultConfiguration;
import org.apache.activemq6.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq6.jms.server.config.JMSConfiguration;
import org.apache.activemq6.jms.server.config.JMSQueueConfiguration;
import org.apache.activemq6.jms.server.config.TopicConfiguration;


/**
 * A JMSConfigurationImpl
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 */
public class JMSConfigurationImpl implements JMSConfiguration
{
   private List<ConnectionFactoryConfiguration> connectionFactoryConfigurations = new ArrayList<ConnectionFactoryConfiguration>();

   private List<JMSQueueConfiguration> queueConfigurations = new ArrayList<JMSQueueConfiguration>();

   private List<TopicConfiguration> topicConfigurations = new ArrayList<TopicConfiguration>();

   private String domain = HornetQDefaultConfiguration.getDefaultJmxDomain();

   private Context context = null;

   // JMSConfiguration implementation -------------------------------

   public JMSConfigurationImpl()
   {
   }

   public List<ConnectionFactoryConfiguration> getConnectionFactoryConfigurations()
   {
      return connectionFactoryConfigurations;
   }

   public JMSConfigurationImpl setConnectionFactoryConfigurations(List<ConnectionFactoryConfiguration> connectionFactoryConfigurations)
   {
      this.connectionFactoryConfigurations = connectionFactoryConfigurations;
      return this;
   }

   public List<JMSQueueConfiguration> getQueueConfigurations()
   {
      return queueConfigurations;
   }

   public JMSConfigurationImpl setQueueConfigurations(List<JMSQueueConfiguration> queueConfigurations)
   {
      this.queueConfigurations = queueConfigurations;
      return this;
   }

   public List<TopicConfiguration> getTopicConfigurations()
   {
      return topicConfigurations;
   }

   public JMSConfigurationImpl setTopicConfigurations(List<TopicConfiguration> topicConfigurations)
   {
      this.topicConfigurations = topicConfigurations;
      return this;
   }

   public Context getContext()
   {
      return context;
   }

   public JMSConfigurationImpl setContext(final Context context)
   {
      this.context = context;
      return this;
   }

   public String getDomain()
   {
      return domain;
   }

   public JMSConfigurationImpl setDomain(final String domain)
   {
      this.domain = domain;
      return this;
   }
}
