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

package org.hornetq.api.core;

import static org.hornetq.api.core.HornetQExceptionType.INVALID_TRANSIENT_QUEUE_USE;

/**
 * An operation failed because a queue exists on the server.
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a> 4/30/12
 */
public final class HornetQInvalidTransientQueueUseException extends HornetQException
{
   private static final long serialVersionUID = -405552292451883063L;

   public HornetQInvalidTransientQueueUseException()
   {
      super(INVALID_TRANSIENT_QUEUE_USE);
   }

   public HornetQInvalidTransientQueueUseException(String msg)
   {
      super(INVALID_TRANSIENT_QUEUE_USE, msg);
   }
}
