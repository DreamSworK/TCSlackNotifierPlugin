package com.tapadoo.slacknotifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jetbrains.buildServer.issueTracker.Issue;
import jetbrains.buildServer.parameters.ParametersProvider;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.settings.ProjectSettingsManager;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.UserSet;
import jetbrains.buildServer.vcs.SelectPrevBuildPolicy;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

public class SlackServerAdapter extends BuildServerAdapter {

    private final SBuildServer buildServer;
    private final SlackConfigProcessor slackConfig;
    private final ProjectSettingsManager projectSettingsManager;
    private final ProjectManager projectManager;
    private Gson gson ;

    public SlackServerAdapter(SBuildServer sBuildServer, ProjectManager projectManager, ProjectSettingsManager projectSettingsManager, SlackConfigProcessor configProcessor)
    {
        this.projectManager = projectManager;
        this.projectSettingsManager = projectSettingsManager;
        this.buildServer = sBuildServer;
        this.slackConfig = configProcessor;
    }

    public void init()
    {
        buildServer.addListener(this);
    }

    private Gson getGson()
    {
        if(gson == null)
        {
            gson = new GsonBuilder().create();
        }
        return gson;
    }

    @Override
    public void buildStarted(@NotNull SRunningBuild build)
    {
        super.buildStarted(build);

        if(!build.isPersonal() && slackConfig.postStarted())
        {
            postStartedBuild(build);
        }
    }

    @Override
    public void buildFinished(@NotNull SRunningBuild build)
    {
        super.buildFinished(build);

        if(!build.isPersonal() && build.getBuildStatus().isSuccessful() && slackConfig.postSuccessful())
        {
            processSuccessfulBuild(build);
        }
        else if (!build.isPersonal() && build.getBuildStatus().isFailed() && slackConfig.postFailed())
        {
            postFailureBuild(build);
        }
    }

    private void postStartedBuild(SRunningBuild build)
    {
        String message = String.format("Project *%s* build started.", build.getFullName());
        postToSlack(build, message, true);
    }

    private void postFailureBuild(SRunningBuild build )
    {
        String message = String.format("Project *%s* build failed!", build.getFullName());
        postToSlack(build, message, false);
    }

    private void processSuccessfulBuild(SRunningBuild build)
    {
        PeriodFormatter durationFormatter = new PeriodFormatterBuilder()
                    .printZeroRarelyFirst()
                    .appendHours()
                    .appendSuffix(" hour", " hours")
                    .appendSeparator(" ")
                    .printZeroRarelyLast()
                    .appendMinutes()
                    .appendSuffix(" minute", " minutes")
                    .appendSeparator(" and ")
                    .appendSeconds()
                    .appendSuffix(" second", " seconds")
                    .toFormatter();

        Duration buildDuration = new Duration(1000 * build.getDuration());
        String message = String.format("Project *%s* successfully built in _%s_", build.getFullName(), durationFormatter.print(buildDuration.toPeriod()));
        postToSlack(build, message, true);
    }

    /**
     * Post a payload to slack with a message and good/bad color. Committer summary is automatically added as an attachment
     * @param build the build the message is relating to
     * @param text main message to include, 'Build X completed...' etc
     * @param goodColor true for 'good' builds, false for danger.
     */
    private void postToSlack(SRunningBuild build, String text, boolean goodColor)
    {
        try {
            SlackProjectSettings projectSettings = (SlackProjectSettings) projectSettingsManager.getSettings(build.getProjectId(),"slackSettings");
            if(!projectSettings.isEnabled())
            {
                return;
            }

            String channel = projectSettings.getChannel();
            if(channel == null || channel.length() == 0)
            {
                channel = slackConfig.getDefaultChannel();
            }

            String postUrl = projectSettings.getPostUrl();
            if(postUrl == null || postUrl.length() == 0)
            {
                postUrl = slackConfig.getPostUrl();
            }
            URL url = new URL(postUrl);

            String iconUrl = projectSettings.getLogoUrl();
            if(iconUrl == null || iconUrl.length() == 0)
            {
                iconUrl = slackConfig.getLogoUrl();
            }

            JsonObject message = new JsonObject();
            message.addProperty("channel", channel);
            message.addProperty("username", "TeamCity");
            message.addProperty("text", text);
            message.addProperty("icon_url", iconUrl);
            message.addProperty("mrkdwn", true);

            JsonArray attachments = new JsonArray();
            JsonObject attachment = new JsonObject();
            attachments.add(attachment);

            ParametersProvider parameters = build.getParametersProvider();
            String repository = parameters.get("vcsroot.url");
            String hash = parameters.get("env.BUILD_VCS_NUMBER");
            String author = parameters.get("env.BUILD_VCS_AUTHOR");
            String email = parameters.get("env.BUILD_VCS_EMAIL");
            String subject = parameters.get("env.BUILD_VCS_SUBJECT");
            String timestamp = parameters.get("env.BUILD_VCS_TIMESTAMP");

            if (author != null && email != null)
            {
                attachment.addProperty("author_name", author);
                attachment.addProperty("author_link", "mailto:" + email);
            }

            if (repository != null && hash != null && subject != null && timestamp != null)
            {
                attachment.addProperty("title", subject);
                attachment.addProperty("title_link", String.format("%s/commit/%s", repository, hash));
                attachment.addProperty("ts", Integer.parseInt(timestamp));
            }

            JsonArray fields = new JsonArray();

            if (projectSettings.isAddBuild()) {
                JsonObject buildField = new JsonObject();
                buildField.addProperty("title", "Build");
                buildField.addProperty("value", build.getBuildNumber());
                buildField.addProperty("short", true);
                fields.add(buildField);
            }

            if (projectSettings.isAddCommitters())
            {
                UserSet<SUser> committers = build.getCommitters(SelectPrevBuildPolicy.SINCE_LAST_BUILD);
                StringBuilder committersString = new StringBuilder();
                for(SUser committer : committers.getUsers())
                {
                    if( committer != null)
                    {
                        String committerName = committer.getName();
                        if(committerName == null || committerName.equals(""))
                        {
                            committerName = committer.getUsername();
                        }

                        if(committerName != null && !committerName.equals(""))
                        {
                            committersString.append(committerName);
                            committersString.append(",");
                        }
                    }
                }
                if(committersString.length() > 0)
                {
                    committersString.deleteCharAt(committersString.length() - 1); // remove the last
                }
                String commitMsg = committersString.toString();

                if(commitMsg.length() > 0)
                {
                    JsonObject field = new JsonObject();
                    field.addProperty("title","Changes By");
                    field.addProperty("value", commitMsg);
                    field.addProperty("short", true);
                    fields.add(field);
                }
            }

            if(projectSettings.isAddIssues() && build.isHasRelatedIssues())
            {
                Collection<Issue> issues = build.getRelatedIssues();
                StringBuilder IssuesString = new StringBuilder();

                for(Issue issue : issues)
                {
                    IssuesString.append(',');
                    IssuesString.append('<');
                    IssuesString.append(issue.getUrl());
                    IssuesString.append('|');
                    IssuesString.append(issue.getId());
                    IssuesString.append('>');
                }
                if(IssuesString.length() > 0)
                {
                    IssuesString.deleteCharAt(0); // delete first ','
                }

                JsonObject field = new JsonObject();
                field.addProperty("title", "Related Issues");
                field.addProperty("value", IssuesString.toString());
                field.addProperty("short", true);
                fields.add(field);
            }

            attachment.addProperty("color", (goodColor ? "good" : "danger"));

            attachment.add("fields", fields);

            if(attachments.size() > 0)
            {
                message.add("attachments", attachments);
            }

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);

            BufferedOutputStream stream = new BufferedOutputStream(conn.getOutputStream());
            String payloadJson = getGson().toJson(message);
            String bodyContents = "payload=" + payloadJson ;
            stream.write(bodyContents.getBytes("utf8"));
            stream.flush();
            stream.close();

            int serverResponseCode = conn.getResponseCode();
            conn.disconnect();
            conn = null;
            url = null;
        }
        catch (MalformedURLException ignored) { }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
