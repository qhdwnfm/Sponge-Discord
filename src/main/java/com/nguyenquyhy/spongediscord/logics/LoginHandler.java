package com.nguyenquyhy.spongediscord.logics;

import com.google.common.util.concurrent.FutureCallback;
import com.nguyenquyhy.spongediscord.SpongeDiscord;
import com.nguyenquyhy.spongediscord.database.IStorage;
import com.nguyenquyhy.spongediscord.models.ChannelConfig;
import com.nguyenquyhy.spongediscord.models.GlobalConfig;
import com.nguyenquyhy.spongediscord.utils.TextUtil;
import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.entities.Channel;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.listener.message.MessageCreateListener;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.source.CommandBlockSource;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;

/**
 * Created by Hy on 8/6/2016.
 */
public class LoginHandler {
    public static boolean loginBotAccount() {
        SpongeDiscord mod = SpongeDiscord.getInstance();
        Logger logger = mod.getLogger();
        GlobalConfig config = mod.getConfig();

        if (StringUtils.isBlank(config.botToken)) {
            logger.warn("No Bot token is available! Messages can only get from and to authenticated players.");
            return false;
        }

        DiscordAPI defaultClient = mod.getBotClient();
        if (defaultClient != null && defaultClient.getToken().equals(config.botToken)) {
            return true;
        }

        if (defaultClient != null) {
            defaultClient.disconnect();
        }

        logger.info("Logging in to bot Discord account...");

        DiscordAPI client = Javacord.getApi(config.botToken, true);
        prepareBotClient(client, null);
        return true;
    }

    public static void loginNormalAccount(Player player) {
        SpongeDiscord mod = SpongeDiscord.getInstance();
        Logger logger = mod.getLogger();
        IStorage storage = mod.getStorage();

        if (storage != null) {
            String cachedToken = mod.getStorage().getToken(player.getUniqueId());
            if (null != cachedToken && !cachedToken.isEmpty()) {
                player.sendMessage(Text.of(TextColors.GRAY, "Logging in to Discord..."));

                DiscordAPI client = Javacord.getApi(cachedToken, false);
                prepareHumanClient(client, player);
            }
        }
    }

    public static CommandResult login(CommandSource commandSource, String email, String password) {
        logout(commandSource, true);

        DiscordAPI client = Javacord.getApi(email, password);
        prepareHumanClient(client, commandSource);

        return CommandResult.success();
    }

    public static CommandResult logout(CommandSource commandSource, boolean isSilence) {
        SpongeDiscord mod = SpongeDiscord.getInstance();

        if (commandSource instanceof Player) {
            Player player = (Player) commandSource;
            UUID playerId = player.getUniqueId();
            try {
                SpongeDiscord.getInstance().getStorage().removeToken(playerId);
            } catch (IOException e) {
                e.printStackTrace();
                commandSource.sendMessage(Text.of(TextColors.RED, "Cannot remove cached token!"));
            }
            mod.removeAndLogoutClient(playerId);
            mod.getUnauthenticatedPlayers().add(player.getUniqueId());

            if (!isSilence)
                commandSource.sendMessage(Text.of(TextColors.YELLOW, "Logged out of Discord!"));
            return CommandResult.success();
        } else if (commandSource instanceof ConsoleSource) {
            mod.removeAndLogoutClient(null);
            commandSource.sendMessage(Text.of("Logged out of Discord!"));
            return CommandResult.success();
        } else if (commandSource instanceof CommandBlockSource) {
            commandSource.sendMessage(Text.of(TextColors.YELLOW, "Cannot log out from command blocks!"));
            return CommandResult.empty();
        }
        return CommandResult.empty();
    }

    private static void prepareBotClient(DiscordAPI client, CommandSource commandSource) {
        SpongeDiscord mod = SpongeDiscord.getInstance();
        Logger logger = mod.getLogger();
        GlobalConfig config = mod.getConfig();

        if (commandSource != null)
            commandSource.sendMessage(Text.of(TextColors.GOLD, TextStyles.BOLD, "Logging in..."));

        client.connect(new FutureCallback<DiscordAPI>() {
            @Override
            public void onSuccess(@Nullable DiscordAPI discordAPI) {
                client.registerListener((MessageCreateListener) (client, message)
                        -> {
                    MessageHandler.discordMessageReceived(message);
                });

                User user = discordAPI.getYourself();
                String name = "unknown";
                if (user != null)
                    name = user.getName();
                String text = "Bot account " + name + " will be used for all unauthenticated users!";
                if (commandSource != null)
                    commandSource.sendMessage(Text.of(TextColors.GOLD, TextStyles.BOLD, text));
                else
                    logger.info(text);

                mod.setBotClient(client);

                for (ChannelConfig channelConfig : config.channels) {
                    if (StringUtils.isNotBlank(channelConfig.discordId)) {
                        Channel channel = client.getChannelById(channelConfig.discordId);
                        if (channel == null) {
                            if (StringUtils.isNotBlank(config.botToken)) {
                                logger.warn("Cannot access channel from Bot account! Please make sure the bot has permission.");
                            } else {
                                logger.info("Accepting channel invite for default account...");
                                acceptInvite(client, channelConfig, commandSource);
                            }
                        } else {
                            channelJoined(client, channelConfig, channel, commandSource);
                        }
                    } else {
                        logger.warn("Channel with empty ID!");
                    }
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                logger.error("Cannot connect to Discord!");
            }
        });
    }

    private static void prepareHumanClient(DiscordAPI client, CommandSource commandSource) {
        SpongeDiscord mod = SpongeDiscord.getInstance();
        GlobalConfig config = mod.getConfig();
        Logger logger = mod.getLogger();

        client.connect(new FutureCallback<DiscordAPI>() {
            @Override
            public void onSuccess(@Nullable DiscordAPI discordAPI) {
                try {
                    String name = client.getYourself().getName();
                    commandSource.sendMessage(Text.of(TextColors.GOLD, TextStyles.BOLD, "You have logged in to Discord account " + name + "!"));

                    if (commandSource instanceof Player) {
                        Player player = (Player) commandSource;
                        UUID playerId = player.getUniqueId();
                        mod.getUnauthenticatedPlayers().remove(playerId);
                        mod.addClient(playerId, client);
                        mod.getStorage().putToken(playerId, client.getToken());
                    } else if (commandSource instanceof ConsoleSource) {
                        commandSource.sendMessage(Text.of("WARNING: This Discord account will be used only for this console session!"));
                        mod.addClient(null, client);
                    } else if (commandSource instanceof CommandBlockSource) {
                        commandSource.sendMessage(Text.of(TextColors.GREEN, "Account is valid!"));
                        return;
                    }

                    for (ChannelConfig channelConfig : config.channels) {
                        if (StringUtils.isNotBlank(channelConfig.discordId)) {
                            Channel channel = client.getChannelById(channelConfig.discordId);
                            if (channel == null) {
                                logger.info("Accepting channel invite");
                                acceptInvite(client, channelConfig, commandSource);
                            } else {
                                channelJoined(client, channelConfig, channel, commandSource);
                            }
                        } else {
                            logger.warn("Channel with empty ID!");
                        }
                    }
                } catch (IOException e) {
                    logger.error("Cannot connect to Discord!", e);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                logger.error("Cannot connect to Discord!", throwable);
            }
        });
    }

    private static Channel acceptInvite(DiscordAPI client, ChannelConfig channelConfig, CommandSource src) {
        SpongeDiscord mod = SpongeDiscord.getInstance();
        Logger logger = mod.getLogger();
        GlobalConfig config = mod.getConfig();

        if (StringUtils.isNotBlank(channelConfig.discordInviteCode)) {
            client.acceptInvite(channelConfig.discordInviteCode, new FutureCallback<Server>() {
                @Override
                public void onSuccess(@Nullable Server server) {
                    Channel channel = client.getChannelById(channelConfig.discordId);
                    channelJoined(client, channelConfig, channel, src);
                }

                @Override
                public void onFailure(Throwable throwable) {

                }
            });
        }
        return null;
    }

    private static void channelJoined(DiscordAPI client, ChannelConfig channelConfig, Channel channel, CommandSource src) {
        SpongeDiscord mod = SpongeDiscord.getInstance();
        Logger logger = mod.getLogger();

        if (channel != null && StringUtils.isNotBlank(channelConfig.discordId) && channelConfig.discord != null) {
            if (client != mod.getBotClient()) {
                String playerName = "console";
                if (src instanceof Player) {
                    Player player = (Player) src;
                    playerName = player.getName();
                }
                if (StringUtils.isNotBlank(channelConfig.discord.joinedTemplate)) {
                    channel.sendMessage(String.format(channelConfig.discord.joinedTemplate, playerName), false);
                }
                logger.info(playerName + " connected to Discord channel " + channelConfig.discordId + ".");
            } else {
                logger.info("Bot account has connected to Discord channel " + channelConfig.discordId + ".");
                if (StringUtils.isNotBlank(channelConfig.discord.serverUpMessage)) {
                    channel.sendMessage(channelConfig.discord.serverUpMessage + TextUtil.SPECIAL_CHAR, false);
                }
            }
        }
    }
}
