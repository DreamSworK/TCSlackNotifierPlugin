package com.tapadoo.slacknotifier;

import jetbrains.buildServer.serverSide.settings.ProjectSettings;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;

/**
 * Created by jasonconnery on 02/03/2014.
 */
public class SlackProjectSettings implements ProjectSettings {

    private String projectId;
    private String channel;
    private String logoUrl;
    private boolean enabled = true ;

    public SlackProjectSettings(String projectId) {
        this.projectId = projectId ;
    }

    public SlackProjectSettings()
    {

    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public boolean isEnabled()
    {
        return this.enabled;
    }

    public void dispose() {

    }

    public void readFrom(Element element) {
        Element channelElement = element.getChild("channel");
        Element logoElement = element.getChild("logo");
        Attribute enabledAttr = element.getAttribute("enabled");

        if( enabledAttr != null )
        {
            try {
                enabled = enabledAttr.getBooleanValue() ;
            } catch (DataConversionException e) {
                enabled = true ;
            }
        }
        else
        {
            enabled = true ;
        }

        if( channelElement != null ) {
            this.channel = channelElement.getText();
        }

        if( logoElement != null )
        {
            this.logoUrl = logoElement.getText();
        }
    }

    public void writeTo(Element element) {

        Element channelElement = new Element("channel");
        channelElement.setText(this.channel);

        Element logoUrlElement = new Element("logoUrl");
        logoUrlElement.setText(this.logoUrl);

        Attribute enabledAttr = new Attribute("enabled",Boolean.toString(enabled)) ;
        element.setAttribute( enabledAttr );

        element.addContent(channelElement);
        element.addContent(logoUrlElement);
    }

}
