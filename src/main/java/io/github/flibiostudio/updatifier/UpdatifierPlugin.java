/*
 * This file is part of Updatifier, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016 - 2016 FlibioStudio <http://github.com/FlibioStudio>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.github.flibiostudio.updatifier;

import static io.github.flibiostudio.updatifier.PluginInfo.DESCRIPTION;
import static io.github.flibiostudio.updatifier.PluginInfo.ID;
import static io.github.flibiostudio.updatifier.PluginInfo.NAME;
import static io.github.flibiostudio.updatifier.PluginInfo.PERM_NOTIFY;
import static io.github.flibiostudio.updatifier.PluginInfo.VERSION;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import me.flibio.updatifier.Updatifier;
import me.flibio.updatifier.UpdatifierService;
import io.github.flibiostudio.updatifier.command.UpdatifierCommands;
import net.minecrell.mcstats.SpongeStatsLite;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;

import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;

@Plugin(id = ID, name = NAME, version = VERSION, description = DESCRIPTION, authors = {"Flibio", "liach", "KingGoesGaming"})
@Updatifier(repoName = "Updatifier", repoOwner = "FlibioStudio", version = "v" + VERSION)
public class UpdatifierPlugin {

    private static Injector injector;
    private final Injector providedInjector;
    private FileManager fileManager;
    private final ConfigurationLoader<CommentedConfigurationNode> defaultRoot;
    private final Logger logger;
    private final SpongeStatsLite statsLite;
    private HashMap<String, String> updates = new HashMap<>();
    private UpdatifierService api;
    private boolean downloadUpdates = false;
    private boolean showChangelogs = false;

    @Inject
    private UpdatifierPlugin(Injector injector, @DefaultConfig(sharedRoot = false) ConfigurationLoader<CommentedConfigurationNode> defaultRoot,
            Logger logger, SpongeStatsLite statsLite) {
        providedInjector = injector;
        this.defaultRoot = defaultRoot;
        this.logger = logger;
        this.statsLite = statsLite;
    }

    public static Injector getInjector() {
        return injector;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public boolean downloadUpdates() {
        return this.downloadUpdates;
    }

    public boolean showChangelogs() {
        return this.showChangelogs;
    }

    public Map<String, String> getUpdates() {
        return ImmutableMap.copyOf(this.updates);
    }

    @Listener
    public void onPreInitialize(GamePreInitializationEvent event) {
        injector = providedInjector;

        this.statsLite.start();
        this.api = new UpdatifierServiceImpl(this);
        this.fileManager = new FileManager(this.logger, this.defaultRoot);
        this.downloadUpdates = this.fileManager.getOrDefault("Download-Updates", Boolean.class, false);
        this.showChangelogs = this.fileManager.getOrDefault("Show-Changelogs", Boolean.class, true);
    }

    @Listener
    public void started(GameStartedServerEvent event) {
        UpdatifierCommands commands = getInjector().getInstance(UpdatifierCommands.class);
        commands.init();
        commands.registerAll();

        Sponge.getPluginManager().getPlugins().forEach(pluginC -> {
            if (pluginC.getInstance().isPresent()) {
                if (pluginC.getInstance().get().getClass().isAnnotationPresent(Updatifier.class)) {
                    if (!this.fileManager.getOrDefault("Blocked-Plugins." + pluginC.getId(), Boolean.class, false)) {
                        Updatifier info = pluginC.getInstance().get().getClass().getAnnotation(Updatifier.class);
                        Task.builder().execute(task -> {
                            boolean available = this.api.updateAvailable(info.repoOwner(), info.repoName(), info.version());
                            if (available) {
                                // Add the plugin to the HashMap
                                this.updates.put(pluginC.getName(), info.repoOwner() + "/" + info.repoName());
                                // Log the messages on the main thread
                                Task.builder().execute(c -> {
                                    this.logger.info("An update is available for " + pluginC.getName() + "!");
                                    if (this.showChangelogs) {
                                        String body = this.api.getBody(info.repoOwner(), info.repoName()).replaceAll("\r", "").replaceAll("\n", "");
                                        if (body.contains("<!--") && body.contains("-->")) {
                                            String result = body.substring(body.indexOf("<!--") + 4, body.indexOf("-->"));
                                            String[] changes = result.split(";");
                                            for (String change : changes) {
                                                if (!change.trim().isEmpty()) {
                                                    this.logger.info("- " + change.trim());
                                                }
                                            }
                                        }
                                    }
                                    if (this.downloadUpdates) {
                                        this.logger.info("It will be downloaded to 'updates/"
                                                + this.api.getFileName(info.repoOwner(), info.repoName()));
                                    } else {
                                        this.logger.info("Download it here: " + "https://github.com/" + info.repoOwner() + "/" + info.repoName()
                                                + "/releases");
                                    }
                                }).submit(this);
                                if (this.downloadUpdates) {
                                    // Download the latest release asset
                                    String url = this.api.getDownloadUrl(info.repoOwner(), info.repoName());
                                    if (!url.isEmpty()) {
                                        try {
                                            URL toDownload = new URL(url);
                                            ReadableByteChannel rbc = Channels.newChannel(toDownload.openStream());
                                            FileOutputStream fos = new FileOutputStream("updates/"
                                                    + this.api.getFileName(info.repoOwner(), info.repoName()));
                                            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                            fos.close();
                                        } catch (Exception e) {
                                            this.logger.error(e.toString());
                                        }
                                    }
                                }
                            }
                        }).async().submit(this);
                    }
                }
            }
        });
    }

    @Listener
    public void onJoin(ClientConnectionEvent.Join event) {
        Player player = event.getTargetEntity();
        if (player.hasPermission(PERM_NOTIFY)) {
            for (String name : this.updates.keySet()) {
                player.sendMessage(Text.of(TextColors.YELLOW, "An update is available for ", TextColors.GREEN, name, "!"));

                String repoOwner = this.updates.get(name).split("/")[0];
                String repoName = this.updates.get(name).split("/")[1];
                if (this.showChangelogs) {
                    String body = this.api.getBody(repoOwner, repoName).replaceAll("\r", "").replaceAll("\n", "");
                    if (body.contains("<!--") && body.contains("-->")) {
                        String result = body.substring(body.indexOf("<!--") + 4, body.indexOf("-->"));
                        String[] changes = result.split(";");
                        for (String change : changes) {
                            if (!change.trim().isEmpty()) {
                                player.sendMessage(Text.of(TextColors.YELLOW, "- ", TextColors.GRAY, change.trim()));
                            }
                        }
                    }
                }
                if (this.downloadUpdates) {
                    player.sendMessage(Text.of(TextColors.YELLOW, "It will be downloaded to ", TextColors.GREEN,
                            "updates/" + api.getFileName(repoOwner, repoName)));
                } else {
                    String releases = "https://github.com/" + repoOwner + "/" + repoName + "/releases";
                    Text githubReleases;
                    try {
                        githubReleases = Text.builder(releases)
                                .onClick(TextActions.openUrl(new URL(releases)))
                                .build();
                    } catch (MalformedURLException e) {
                        // Silently fail and send a non-clickable link
                        githubReleases = Text.of(releases);
                    }
                    player.sendMessage(Text.of(TextColors.YELLOW, "Download it here: ",
                            TextColors.GREEN, githubReleases));
                }
            }
        }
    }
}
