package fun.eqad.eskin;

import fun.eqad.eskin.manager.SkinManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.skinsrestorer.api.SkinsRestorerProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public final class ESkinBukkit extends JavaPlugin implements Listener {

    private SkinManager skinManager;
    private FileConfiguration config;
    private File configFile;
    private FileConfiguration msgConfig;
    private FileConfiguration mainConfig;

    @Override
    public void onEnable() {
        this.skinManager = new SkinManager(getLogger());

        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("ESkin已成功加载");
    }

    @Override
    public void onDisable() {
        saveConfig();
        getLogger().info("ESkin已成功卸载");
    }

    private void loadConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        configFile = new File(getDataFolder(), "data.yml");

        if (!configFile.exists()) {
            try (InputStream in = getResource("data.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                } else {
                    configFile.createNewFile();
                }
            } catch (IOException e) {
                getLogger().warning("无法创建配置文件: " + e.getMessage());
                e.printStackTrace();
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        File msgFile = new File(getDataFolder(), "msg.yml");
        if (!msgFile.exists()) {
            try (InputStream in = getResource("msg.yml")) {
                if (in != null) {
                    Files.copy(in, msgFile.toPath());
                } else {
                    msgFile.createNewFile();
                }
            } catch (IOException e) {
                getLogger().warning("无法创建配置文件: " + e.getMessage());
                e.printStackTrace();
            }
        }
        msgConfig = YamlConfiguration.loadConfiguration(msgFile);

        File mainConfigFile = new File(getDataFolder(), "config.yml");
        if (!mainConfigFile.exists()) {
            try (InputStream in = getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, mainConfigFile.toPath());
                } else {
                    mainConfigFile.createNewFile();
                }
            } catch (IOException e) {
                getLogger().warning("无法创建配置文件: " + e.getMessage());
                e.printStackTrace();
            }
        }
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);

        skinManager.clearSkinServers();
        ConfigurationSection skinServers = mainConfig.getConfigurationSection("skin-servers");
        if (skinServers != null) {
            for (String key : skinServers.getKeys(false)) {
                String csl = skinServers.getString(key + ".csl");
                String texture = skinServers.getString(key + ".texture");
                skinManager.addSkinServer(key, csl, texture);
            }
        }
    }

    @Override
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().warning("无法保存配置文件: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
            SkinManager.SkinSearchResult result = null;
            try {
                result = skinManager.getPlayerTextureId(playerName);

                if (result == null) {
                    getLogger().info("未能在皮肤站上读取到" + playerName + "的皮肤数据");
                    return;
                }

                String currentTextureId = result.textureId;
                String cachedTextureId = config.getString(player.getUniqueId().toString() + ".texture");
                String cachedServerName = config.getString(player.getUniqueId().toString() + ".server");

                if (currentTextureId.equals(cachedTextureId) && result.server.name.equals(cachedServerName)) {
                    getLogger().info(playerName + "的皮肤未变化, 无需更新");
                    return;
                }

                skinManager.applySkinFromTextureId(player.getUniqueId(), playerName, result);
                SkinsRestorerProvider.get().getSkinApplier(Player.class).applySkin(player);

                config.set(player.getUniqueId().toString() + ".texture", currentTextureId);
                config.set(player.getUniqueId().toString() + ".server", result.server.name);
                saveConfig();

                String successMsg = msgConfig.getString("success").replace("%name%", result.server.name);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));

            } catch (Exception e) {
                getLogger().warning("出现意外错误: " + e.getMessage());
            }
        }, 20L);
    }
}
