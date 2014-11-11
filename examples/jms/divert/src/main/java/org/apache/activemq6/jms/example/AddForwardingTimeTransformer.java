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
package org.apache.activemq6.jms.example;

import org.apache.activemq6.api.core.SimpleString;
import org.apache.activemq6.core.server.ServerMessage;
import org.apache.activemq6.core.server.cluster.Transformer;

/**
 * A AddForwardingTimeTransformer
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 *
 */
public class AddForwardingTimeTransformer implements Transformer
{
   public ServerMessage transform(final ServerMessage message)
   {
      message.putLongProperty(new SimpleString("time_of_forward"), System.currentTimeMillis());

      return message;
   }

}