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
package org.hornetq.core.protocol.stomp;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 *         3/8/12
 *
 * Logger Code 22
 *
 * each message id must be 6 digits long starting with 10, the 3rd digit donates the level so
 *
 * INF0  1
 * WARN  2
 * DEBUG 3
 * ERROR 4
 * TRACE 5
 * FATAL 6
 *
 * so an INFO message would be 101000 to 101999
 */

@MessageLogger(projectCode = "HQ")
public interface HornetQStompProtocolLogger
{
   /**
    * The default logger.
    */
   HornetQStompProtocolLogger LOGGER = Logger.getMessageLogger(HornetQStompProtocolLogger.class, HornetQStompProtocolLogger.class.getPackage().getName());

   @LogMessage(level = Logger.Level.WARN)
   @Message(id = 222068, value = "connection closed {0}", format = Message.Format.MESSAGE_FORMAT)
   void connectionClosed(StompConnection connection);

   @LogMessage(level = Logger.Level.ERROR)
   @Message(id = 224023, value = "Unable to send frame {0}", format = Message.Format.MESSAGE_FORMAT)
   void errorSendingFrame(@Cause Exception e, StompFrame frame);
}
