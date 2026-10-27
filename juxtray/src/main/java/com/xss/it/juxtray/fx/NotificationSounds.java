package com.xss.it.juxtray.fx;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.media.AudioClip;

/**
 * Plays notification sounds with JavaFX's {@link AudioClip}.
 *
 * <p>An {@code AudioClip} decodes the whole file into memory once and then
 * plays it with very low latency, which suits short notification sounds.
 * It supports the formats of JavaFX Media, including WAV and MP3, from any
 * URL JavaFX understands ({@code file:}, {@code jar:}, {@code http:} ...).
 * Clips are cached per URL, so a sound is only decoded the first time it is
 * played.</p>
 *
 * <p>A sound must never break a notification: a missing file or an
 * unsupported format is logged once and then ignored.</p>
 *
 * <p>All methods must be called on the JavaFX Application Thread.</p>
 */
final class NotificationSounds {

    /** Logger for sounds that cannot be loaded. */
    private static final Logger LOG = System.getLogger(NotificationSounds.class.getName());

    /** Loaded clips by URL; {@code null} values mark URLs that failed to load. */
    private final Map<String, AudioClip> clips = new HashMap<>();

    /**
     * Plays a sound. Does nothing for a {@code null} or blank URL, or a sound
     * that failed to load before.
     *
     * @param url    the sound's URL
     * @param volume volume from 0 (silent) to 1 (full); clamped into that range
     */
    void play(String url, double volume) {
        if (url == null || url.isBlank()) {
            return;
        }
        // Not computeIfAbsent: it would not remember failures (null), and would retry and log every time.
        if (!clips.containsKey(url)) {
            clips.put(url, load(url));
        }
        AudioClip clip = clips.get(url);
        if (clip != null) {
            clip.play(Math.clamp(volume, 0.0, 1.0));
        }
    }

    /**
     * Stops every playing sound and forgets the cached clips.
     */
    void dispose() {
        clips.values().forEach(clip -> {
            if (clip != null) {
                clip.stop();
            }
        });
        clips.clear();
    }

    /**
     * Loads one clip.
     *
     * @param url the sound's URL
     * @return the clip, or {@code null} if it cannot be loaded (logged)
     */
    private static AudioClip load(String url) {
        try {
            return new AudioClip(url);
        } catch (RuntimeException e) {
            // MediaException for unsupported formats, IllegalArgumentException for bad URLs.
            LOG.log(Level.WARNING, "Notification sound cannot be played: " + url, e);
            return null;
        }
    }
}
