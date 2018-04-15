package com.tapadoo.slacknotifier;

import jetbrains.buildServer.serverSide.settings.ProjectSettings;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;

public class SlackProjectSettings implements ProjectSettings {

    public String projectId;

    private static final String ATTRIBUTE_ENABLED = "enabled";
    private static final String ATTRIBUTE_BUILD = "build";
    private static final String ATTRIBUTE_ISSUES = "issues";
    private static final String ATTRIBUTE_COMMITTERS = "committers";
    private boolean enabled = true;
    private boolean build = true;
    private boolean issues = true;
    private boolean committers = true;

    private static final String ELEMENT_CHANNEL = "channel";
    private static final String ELEMENT_POST_URL = "postUrl";
    private static final String ELEMENT_LOGO_URL = "logoUrl";
    private String channel;
    private String postUrl;
    private String logoUrl;

    public SlackProjectSettings(String projectId) {
        this.projectId = projectId ;
    }

    public SlackProjectSettings() { }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getPostUrl() {
        return postUrl;
    }

    public void setPostUrl(String postUrl) {
        this.postUrl = postUrl;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isAddBuild() {
        return this.build;
    }

    public boolean isAddIssues() {
        return this.issues;
    }

    public boolean isAddCommitters() {
        return this.committers;
    }

    public void dispose() { }

    private boolean readAttribute(Element rootElement, String attributeName)
    {
        boolean result;
        Attribute attribute = rootElement.getAttribute(attributeName);
        if(attribute != null)
        {
            try {
                result = attribute.getBooleanValue();
            } catch (DataConversionException e) {
                result = true;
            }
        }
        else
        {
            result = true;
        }
        return result;
    }

    private String readElement(Element rootElement, String elementName)
    {
        Element element = rootElement.getChild(elementName);
        if(element != null)
        {
            return element.getText();
        }
        else
        {
            return null;
        }
    }

    public void readFrom(Element element)
    {
        this.enabled = readAttribute(element, ATTRIBUTE_ENABLED);
        this.build = readAttribute(element, ATTRIBUTE_BUILD);
        this.issues = readAttribute(element, ATTRIBUTE_ISSUES);
        this.committers = readAttribute(element, ATTRIBUTE_COMMITTERS);

        this.channel = readElement(element, ELEMENT_CHANNEL);
        this.postUrl = readElement(element, ELEMENT_POST_URL);
        this.logoUrl = readElement(element, ELEMENT_LOGO_URL);
    }

    private void writeElement(Element rootElement, String elementName, String elementValue)
    {
        Element element = new Element(elementName);
        element.setText(elementValue);
        rootElement.addContent(element);
    }

    private void writeAttribute(Element rootElement, String attributeName, Boolean attributeValue)
    {
        Attribute attribute = new Attribute(attributeName, Boolean.toString(attributeValue));
        rootElement.setAttribute(attribute);
    }

    public void writeTo(Element element)
    {
        writeAttribute(element, ATTRIBUTE_ENABLED, this.enabled);
        writeAttribute(element, ATTRIBUTE_BUILD, this.build);
        writeAttribute(element, ATTRIBUTE_ISSUES, this.issues);
        writeAttribute(element, ATTRIBUTE_COMMITTERS, this.committers);

        writeElement(element, ELEMENT_CHANNEL, this.channel);
        writeElement(element, ELEMENT_POST_URL, this.postUrl);
        writeElement(element, ELEMENT_LOGO_URL, this.logoUrl);
    }
}
