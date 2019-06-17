package com.axway.maven.apigw.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class XMLUtils {

	public static Document parseXml(InputStream in, boolean namespaceAware) throws XMLUtilsException {
		try {
			DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
			df.setNamespaceAware(namespaceAware);
			DocumentBuilder builder = df.newDocumentBuilder();
			return builder.parse(in);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new XMLUtilsException("Error on parsing XML input stream", e);
		}
	}

	public static Document parseXml(File file, boolean namespaceAware) {
		try {
			try (InputStream in = new FileInputStream(file)) {
				return parseXml(in, namespaceAware);
			}
		} catch (IOException e) {
			throw new XMLUtilsException("Error on parsing XML file", e);
		}
	}

	public static Document createDocument(boolean namespaceAware) {
		try {
			DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
			df.setNamespaceAware(namespaceAware);
			DocumentBuilder builder = df.newDocumentBuilder();
			return builder.newDocument();
		} catch (ParserConfigurationException e) {
			throw new XMLUtilsException("Error creating document", e);
		}
	}

	public static void writeDocument(Document doc, File file) {
		try {
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			StreamResult result = new StreamResult(file);

			transformer.transform(new DOMSource(doc), result);
		} catch (TransformerException e) {
			throw new XMLUtilsException("Error on writing document", e);
		}
	}

	public static Element getChildElement(Node parent, String localName) {
		if (localName == null || localName.isEmpty()) {
			throw new XMLUtilsException("Local name must not be null or empty");
		}

		Node child = null;
		if (parent != null) {
			child = parent.getFirstChild();
			while (child != null) {
				if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
					return (Element) child;
				}
				child = child.getNextSibling();
			}
		}
		return null;
	}

}
