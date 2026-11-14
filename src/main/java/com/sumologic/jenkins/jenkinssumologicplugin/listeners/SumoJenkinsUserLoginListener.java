package com.sumologic.jenkins.jenkinssumologicplugin.listeners;

import com.sumologic.jenkins.jenkinssumologicplugin.PluginDescriptorImpl;
import com.sumologic.jenkins.jenkinssumologicplugin.constants.AuditEventTypeEnum;
import hudson.Extension;
import jenkins.security.SecurityListener;
import org.acegisecurity.userdetails.UserDetails;

import edu.umd.cs.findbugs.annotations.NonNull;

import static com.sumologic.jenkins.jenkinssumologicplugin.utility.CommonModelFactory.captureUserLoginEvent;

/**
 * Sumo Logic plugin for Jenkins model.
 * <p>¬
 * Security Listener for Audit Related information
 * <p>
 * Created by Sourabh Jain on 5/2019.
 */
@Extension
public class SumoJenkinsUserLoginListener extends SecurityListener {

    @Override
    protected void authenticated(@NonNull UserDetails details) {
        PluginDescriptorImpl pluginDescriptor = PluginDescriptorImpl.getInstance();
        if (pluginDescriptor.isAuditLogEnabled()) {
            captureUserLoginEvent(details.getUsername(), AuditEventTypeEnum.LOGIN);
        }
    }

    @Override
    protected void failedToAuthenticate(@NonNull String username) {
        PluginDescriptorImpl pluginDescriptor = PluginDescriptorImpl.getInstance();
        if (pluginDescriptor.isAuditLogEnabled()) {
            captureUserLoginEvent(username, AuditEventTypeEnum.LOGIN_FAILURE);
        }
    }

    @Override
    protected void loggedIn(@NonNull String username) {
        //Covered by Authenticated
    }

    @Override
    protected void failedToLogIn(@NonNull String username) {
        //Covered by failedToAuthenticate
    }

    @Override
    protected void loggedOut(@NonNull String username) {
        PluginDescriptorImpl pluginDescriptor = PluginDescriptorImpl.getInstance();
        if (pluginDescriptor.isAuditLogEnabled()) {
            captureUserLoginEvent(username, AuditEventTypeEnum.LOGOUT);
        }
    }
}
