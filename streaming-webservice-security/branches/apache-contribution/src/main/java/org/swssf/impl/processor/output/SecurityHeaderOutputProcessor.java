/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.swssf.impl.processor.output;

import org.swssf.ext.*;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processor to build the Security Header structure
 *
 * @author $Author$
 * @version $Revision$ $Date$
 */
public class SecurityHeaderOutputProcessor extends AbstractOutputProcessor {

    public SecurityHeaderOutputProcessor(SecurityProperties securityProperties, Constants.Action action) throws WSSecurityException {
        super(securityProperties, action);
        setPhase(Constants.Phase.PREPROCESSING);
    }

    @Override
    public void processEvent(XMLEvent xmlEvent, OutputProcessorChain outputProcessorChain) throws XMLStreamException, WSSecurityException {

        boolean eventHandled = false;
        int level = outputProcessorChain.getDocumentContext().getDocumentLevel();

        String soapMessageVersion = outputProcessorChain.getDocumentContext().getSOAPMessageVersionNamespace();

        if (xmlEvent.isStartElement()) {
            StartElement startElement = xmlEvent.asStartElement();

            if (level == 1 && soapMessageVersion == null) {
                throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, "notASOAPMessage");
            } else if (level == 1 && soapMessageVersion != null) {
                //set correct namespace on secure parts
                List<SecurePart> securePartList = securityProperties.getEncryptionSecureParts();
                for (int i = 0; i < securePartList.size(); i++) {
                    SecurePart securePart = securePartList.get(i);
                    if (securePart.getName().equals("Body") && securePart.getNamespace().equals("*")) {
                        securePart.setNamespace(soapMessageVersion);
                        break;
                    }
                }
                securePartList = securityProperties.getSignatureSecureParts();
                for (int j = 0; j < securePartList.size(); j++) {
                    SecurePart securePart = securePartList.get(j);
                    if (securePart.getName().equals("Body") && securePart.getNamespace().equals("*")) {
                        securePart.setNamespace(soapMessageVersion);
                    }
                }
            } else if (level == 3 && startElement.getName().equals(Constants.TAG_wsse_Security)) {
                if (Utils.isResponsibleActorOrRole(startElement, soapMessageVersion, getSecurityProperties().getActor())) {
                    outputProcessorChain.getDocumentContext().setInSecurityHeader(true);
                    //remove this processor. its no longer needed.
                    outputProcessorChain.removeProcessor(this);
                }
            } else if (level == 2
                    && startElement.getName().getLocalPart().equals(Constants.TAG_soap_Body_LocalName)
                    && startElement.getName().getNamespaceURI().equals(soapMessageVersion)) {
                //hmm it seems we don't have a soap header in the current document
                //so output one and add securityHeader

                //create subchain and output soap-header and securityHeader
                OutputProcessorChain subOutputProcessorChain = outputProcessorChain.createSubChain(this);
                createStartElementAndOutputAsEvent(subOutputProcessorChain,
                        new QName(soapMessageVersion, Constants.TAG_soap_Header_LocalName, Constants.PREFIX_SOAPENV), null);
                buildSecurityHeader(soapMessageVersion, subOutputProcessorChain);
                createEndElementAndOutputAsEvent(subOutputProcessorChain,
                        new QName(soapMessageVersion, Constants.TAG_soap_Header_LocalName, Constants.PREFIX_SOAPENV));

                //output current soap-header event
                outputProcessorChain.processEvent(xmlEvent);
                //remove this processor. its no longer needed.
                outputProcessorChain.removeProcessor(this);

                eventHandled = true;
            }
        } else if (xmlEvent.isEndElement()) {
            EndElement endElement = xmlEvent.asEndElement();
            if (level == 2 && endElement.getName().equals(Constants.TAG_wsse_Security)) {
                outputProcessorChain.getDocumentContext().setInSecurityHeader(false);
            } else if (level == 1 && endElement.getName().getLocalPart().equals(Constants.TAG_soap_Header_LocalName)
                    && endElement.getName().getNamespaceURI().equals(soapMessageVersion)) {
                OutputProcessorChain subOutputProcessorChain = outputProcessorChain.createSubChain(this);
                buildSecurityHeader(soapMessageVersion, subOutputProcessorChain);
                //output current soap-header event
                outputProcessorChain.processEvent(xmlEvent);
                //remove this processor. its no longer needed.
                outputProcessorChain.removeProcessor(this);

                eventHandled = true;
            }
        }

        if (!eventHandled) {
            outputProcessorChain.processEvent(xmlEvent);
        }
    }

    private void buildSecurityHeader(String soapMessageVersion, OutputProcessorChain subOutputProcessorChain) throws XMLStreamException, WSSecurityException {
        Map<QName, String> attributes = new HashMap<QName, String>();
        final String actor = getSecurityProperties().getActor();
        if (actor != null && !"".equals(actor)) {
            if (Constants.NS_SOAP11.equals(soapMessageVersion)) {
                attributes.put(Constants.ATT_soap11_Actor, actor);
            } else {
                attributes.put(Constants.ATT_soap12_Role, actor);
            }
        }
        subOutputProcessorChain.getDocumentContext().setInSecurityHeader(true);
        createStartElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_Security, attributes);
        createEndElementAndOutputAsEvent(subOutputProcessorChain, Constants.TAG_wsse_Security);
        subOutputProcessorChain.getDocumentContext().setInSecurityHeader(false);
    }
}