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
package org.apache.activemq6.rest.queue;

import org.apache.activemq6.api.core.HornetQException;
import org.apache.activemq6.api.core.client.ClientMessage;
import org.apache.activemq6.api.core.client.ClientProducer;
import org.apache.activemq6.rest.HornetQRestLogger;

import javax.ws.rs.POST;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;

/**
 * Implements simple "create" link.  Returns 201 with Location of created resource as per HTTP
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class PostMessageDupsOk extends PostMessage
{

   public void publish(HttpHeaders headers, byte[] body, boolean durable,
                       Long ttl,
                       Long expiration,
                       Integer priority) throws Exception
   {
      Pooled pooled = getPooled();
      try
      {
         ClientProducer producer = pooled.producer;
         ClientMessage message = createHornetQMessage(headers, body, durable, ttl, expiration, priority, pooled.session);
         producer.send(message);
         HornetQRestLogger.LOGGER.debug("Sent message: " + message);
         pool.add(pooled);
      }
      catch (Exception ex)
      {
         try
         {
            pooled.session.close();
         }
         catch (HornetQException e)
         {
         }
         addPooled();
         throw ex;
      }
   }

   @POST
   public Response create(@Context HttpHeaders headers,
                          @QueryParam("durable") Boolean durable,
                          @QueryParam("ttl") Long ttl,
                          @QueryParam("expiration") Long expiration,
                          @QueryParam("priority") Integer priority,
                          @Context UriInfo uriInfo,
                          byte[] body)
   {
      HornetQRestLogger.LOGGER.debug("Handling POST request for \"" + uriInfo.getRequestUri() + "\"");

      try
      {
         boolean isDurable = defaultDurable;
         if (durable != null)
         {
            isDurable = durable.booleanValue();
         }
         publish(headers, body, isDurable, ttl, expiration, priority);
      }
      catch (Exception e)
      {
         Response error = Response.serverError()
                 .entity("Problem posting message: " + e.getMessage())
                 .type("text/plain")
                 .build();
         throw new WebApplicationException(e, error);
      }
      Response.ResponseBuilder builder = Response.status(201);
      UriBuilder nextBuilder = uriInfo.getAbsolutePathBuilder();
      URI next = nextBuilder.build();
      serviceManager.getLinkStrategy().setLinkHeader(builder, "create-next", "create-next", next.toString(), "*/*");
      return builder.build();
   }
}