package fun.eqad.eskin;

import org.bukkit.ChatColor;
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
                getLogger().warning("无法创建消息配置文件: " + e.getMessage());
                e.printStackTrace();
            }
        }
        msgConfig = YamlConfiguration.loadConfiguration(msgFile);
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
                SkinsRestorerProvider.get().getSkinApplier(Player.class).applySkin(player);

                config.set(player.getUniqueId().toString(), currentTextureId);
                saveConfig();

                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msgConfig.getString("success")));

            } catch (Exception e) {
                getLogger().warning(playerName + "的皮肤更新失败: " + e.getMessage());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msgConfig.getString("failed")));
            }
        }, 20L);
    }
}
