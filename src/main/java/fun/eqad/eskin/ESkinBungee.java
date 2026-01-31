package fun.eqad.eskin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import net.skinsrestorer.api.SkinsRestorerProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

public final class ESkinBungee extends Plugin implements Listener {

    private SkinManager skinManager;
    private Configuration config;
    private Configuration msgConfig;

    @Override
    public void onEnable() {
        this.skinManager = new SkinManager(getLogger());

        loadConfig();

        getProxy().getPluginManager().registerListener(this, this);

        getLogger().info("ESkin已成功加载");
    }

    @Override
    public void onDisable() {
        saveConfig();
        getLogger().info("ESkin已成功卸载");
    }

    private void loadConfig() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }

            File configFile = new File(getDataFolder(), "data.yml");

            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("data.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    } else {
                        configFile.createNewFile();
                    }
                }
            }

            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);

            File msgFile = new File(getDataFolder(), "msg.yml");
            if (!msgFile.exists()) {
                try (InputStream in = getResourceAsStream("msg.yml")) {
                    if (in != null) {
                        Files.copy(in, msgFile.toPath());
                    } else {
                        msgFile.createNewFile();
                    }
                }
            }
            msgConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(msgFile);

        } catch (IOException e) {
            getLogger().warning("无法加载配置文件: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config,
                    new File(getDataFolder(), "data.yml"));
        } catch (IOException e) {
            getLogger().warning("无法保存配置文件: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String playerName = player.getName();

        getProxy().getScheduler().schedule(this, () -> {
            try {
                String currentTextureId = skinManager.getPlayerTextureId(playerName);

                if (currentTextureId == null) {
                    getLogger().info("未能在皮肤站上读取到" + playerName + "的皮肤数据");
                    return;
                }

                String cachedTextureId = config.getString(player.getUniqueId().toString());

                if (currentTextureId.equals(cachedTextureId)) {
                    getLogger().info(playerName + "的皮肤未变化, 无需更新");
                    return;
                }

                skinManager.applySkinFromTextureId(player.getUniqueId(), playerName, currentTextureId);
                SkinsRestorerProvider.get().getSkinApplier(ProxiedPlayer.class).applySkin(player);

                config.set(player.getUniqueId().toString(), currentTextureId);
                saveConfig();

                player.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', msgConfig.getString("success"))));

            } catch (Exception e) {
                getLogger().warning(playerName + "的皮肤更新失败: " + e.getMessage());
                player.sendMessage(new TextComponent(ChatColor.translateAlternateColorCodes('&', msgConfig.getString("failed"))));
            }
        }, 1, TimeUnit.SECONDS);
    }
}
