package com.nguyenquyhy.discordbridge.models;

import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;

/**
 * Created by Hy on 10/13/2016.
 */
@ConfigSerializable
public class ChannelMinecraftConfig {
    void initializeDefault() {
        chatTemplate = "&7<%a> &f%s";
    }

    @Setting
    public String chatTemplate;
}
