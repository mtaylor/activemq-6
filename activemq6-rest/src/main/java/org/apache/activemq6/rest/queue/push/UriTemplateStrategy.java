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
package org.apache.activemq6.rest.queue.push;

import org.apache.activemq6.api.core.client.ClientMessage;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class UriTemplateStrategy extends UriStrategy
{
   protected String createUri(ClientMessage message)
   {
      String dupId = registration.getId() + "-" + message.getMessageID() + "-" + message.getTimestamp();
      String uri = targetUri.build(dupId).toString();
      return uri;
   }
}