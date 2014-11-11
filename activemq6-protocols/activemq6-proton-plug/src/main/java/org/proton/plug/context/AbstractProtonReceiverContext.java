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

package org.proton.plug.context;

import org.apache.qpid.proton.engine.Receiver;
import org.proton.plug.AMQPSessionCallback;
import org.proton.plug.exceptions.HornetQAMQPException;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 *         <p/>
 *         handles incoming messages via a Proton Receiver and forwards them to HornetQ
 */
public abstract class AbstractProtonReceiverContext extends ProtonInitializable implements ProtonDeliveryHandler
{
   protected final AbstractConnectionContext connection;

   protected final AbstractProtonSessionContext protonSession;

   protected final Receiver receiver;

   protected final String address;

   protected final AMQPSessionCallback sessionSPI;

   public AbstractProtonReceiverContext(AMQPSessionCallback sessionSPI, AbstractConnectionContext connection, AbstractProtonSessionContext protonSession, Receiver receiver)
   {
      this.connection = connection;
      this.protonSession = protonSession;
      this.receiver = receiver;
      if (receiver.getRemoteTarget() != null)
      {
         this.address = receiver.getRemoteTarget().getAddress();
      }
      else
      {
         this.address = null;
      }
      this.sessionSPI = sessionSPI;
   }

   @Override
   public void close() throws HornetQAMQPException
   {
      protonSession.removeReceiver(receiver);
   }

   public void flow(int credits)
   {
      synchronized (connection.getLock())
      {
         receiver.flow(credits);
      }
      connection.flush();
   }
}
