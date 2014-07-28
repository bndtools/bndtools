package org.bndtools.api;

/**
 * A means to be called at startup and shutdown. 
 * Plugin must be referenced in _Plugin.xml file.
 */
public interface IStartupParticipant {
    void start();

    void stop();
}
