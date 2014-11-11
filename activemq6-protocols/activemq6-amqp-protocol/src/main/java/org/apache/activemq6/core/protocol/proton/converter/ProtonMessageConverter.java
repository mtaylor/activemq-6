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

package org.apache.activemq6.core.protocol.proton.converter;

import org.apache.qpid.proton.jms.EncodedMessage;
import org.apache.qpid.proton.jms.InboundTransformer;
import org.apache.qpid.proton.jms.JMSMappingInboundTransformer;
import org.apache.qpid.proton.jms.JMSMappingOutboundTransformer;
import org.apache.activemq6.core.protocol.proton.converter.jms.ServerJMSMessage;
import org.apache.activemq6.core.server.ServerMessage;
import org.apache.activemq6.spi.core.protocol.MessageConverter;
import org.apache.activemq6.utils.IDGenerator;

/**
 * @author Clebert Suconic
 */

public class ProtonMessageConverter implements MessageConverter
{


   HornetQJMSVendor hornetQJMSVendor;

   public ProtonMessageConverter(IDGenerator idGenerator)
   {
      hornetQJMSVendor = new HornetQJMSVendor(idGenerator);
      inboundTransformer = new JMSMappingInboundTransformer(hornetQJMSVendor);
      outboundTransformer = new JMSMappingOutboundTransformer(hornetQJMSVendor);
   }

   private final InboundTransformer inboundTransformer;
   private final JMSMappingOutboundTransformer outboundTransformer;

   @Override
   public ServerMessage inbound(Object messageSource) throws Exception
   {
      ServerJMSMessage jmsMessage = inboundJMSType((EncodedMessage) messageSource);

      return (ServerMessage)jmsMessage.getInnerMessage();
   }

   /**
    * Just create the JMS Part of the inbound (for testing)
    * @param messageSource
    * @return
    * @throws Exception
    */
   public ServerJMSMessage inboundJMSType(EncodedMessage messageSource) throws Exception
   {
      EncodedMessage encodedMessageSource = messageSource;
      ServerJMSMessage transformedMessage = (ServerJMSMessage)inboundTransformer.transform(encodedMessageSource);

      transformedMessage.encode();

      return transformedMessage;
   }


   @Override
   public Object outbound(ServerMessage messageOutbound, int deliveryCount) throws Exception
   {
      ServerJMSMessage jmsMessage = hornetQJMSVendor.wrapMessage(messageOutbound.getType(), messageOutbound, deliveryCount);
      jmsMessage.decode();

      return outboundTransformer.convert(jmsMessage);
   }
}
