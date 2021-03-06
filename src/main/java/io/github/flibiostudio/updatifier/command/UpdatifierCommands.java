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

package io.github.flibiostudio.updatifier.command;

import static io.github.flibiostudio.updatifier.UpdatifierPlugin.getInjector;

import com.google.inject.Inject;
import io.github.flibiostudio.updatifier.PluginInfo;
import io.github.flibiostudio.updatifier.UpdatifierPlugin;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

/**
 * Utility command class for Updatifier plugin.
 */
public final class UpdatifierCommands {

    private final UpdatifierPlugin plugin;
    private CommandSpec commandGetUpdates;

    @Inject
    private UpdatifierCommands(UpdatifierPlugin plugin) {
        this.plugin = plugin;
    }

    public CommandSpec getCommandGetUpdates() {
        return this.commandGetUpdates;
    }

    public void init() {
        commandGetUpdates = CommandSpec.builder()
                .executor(getInjector().getInstance(GetUpdatesExecutor.class))
                .permission(PluginInfo.PERM_NOTIFY)
                .description(Text.of("Get available plugin updates."))
                .arguments()
                .build();
    }

    public void registerAll() {
        Sponge.getCommandManager().register(plugin, commandGetUpdates, "getUpdates", "updates");
    }

}
