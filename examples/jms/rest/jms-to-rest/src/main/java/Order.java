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
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@XmlRootElement(name="order")
public class Order implements Serializable
{
   private String name;
   private String amount;
   private String item;

   public Order()
   {
   }

   public Order(String name, String amount, String item)
   {
      this.name = name;
      this.amount = amount;
      this.item = item;
   }

   public String getName()
   {
      return name;
   }

   public void setName(String name)
   {
      this.name = name;
   }

   public String getAmount()
   {
      return amount;
   }

   public void setAmount(String amount)
   {
      this.amount = amount;
   }

   public String getItem()
   {
      return item;
   }

   public void setItem(String item)
   {
      this.item = item;
   }

   @Override
   public String toString()
   {
      return "Order{" +
              "name='" + name + '\'' +
              ", amount='" + amount + '\'' +
              ", item='" + item + '\'' +
              '}';
   }
}
