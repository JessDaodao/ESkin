package fun.eqad.eskin;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.storage.PlayerStorage;
import net.skinsrestorer.api.storage.SkinStorage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ESkin extends Plugin implements Listener {

    private static final String SKIN_API_URL = "https://helloskin.cn/%s.json";
    private static final String TEXTURE_URL = "https://helloskin.cn/textures/%s";

    private SkinsRestorer skinsRestorerAPI;
    private SkinStorage skinStorage;
    private PlayerStorage playerStorage;
    private Configuration config;

    @Override
    public void onEnable() {
        this.skinsRestorerAPI = SkinsRestorerProvider.get();
        this.skinStorage = skinsRestorerAPI.getSkinStorage();
        this.playerStorage = skinsRestorerAPI.getPlayerStorage();

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

            File configFile = new File(getDataFolder(), "player_skins.yml");

            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("player_skins.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    } else {
                        configFile.createNewFile();
                    }
                }
            }

            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            getLogger().warning("无法加载配置文件: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config,
                    new File(getDataFolder(), "player_skins.yml"));
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
                String currentTextureId = getPlayerTextureId(playerName);

                if (currentTextureId == null) {
                    getLogger().info("未能在皮肤站上读取到" + playerName + "的皮肤数据");
                    return;
                }

                String cachedTextureId = config.getString(player.getUniqueId().toString());

                if (currentTextureId.equals(cachedTextureId)) {
                    getLogger().info(playerName + "的皮肤未变化, 无需更新");
                    return;
                }

                applySkinFromTextureId(player, currentTextureId);

                config.set(player.getUniqueId().toString(), currentTextureId);
                saveConfig();

            } catch (Exception e) {
                getLogger().warning(playerName + "的皮肤更新失败: " + e.getMessage());
                player.sendMessage(new TextComponent("§8[§b皮肤系统§8] §l>>>>>> \n §c无法在HelloSkin更新你的皮肤, 请联系服务器管理员"));
            }
        }, 1, TimeUnit.SECONDS);
    }

    private String getPlayerTextureId(String playerName) throws IOException {
        String apiUrl = String.format(SKIN_API_URL, playerName);
        String jsonResponse = HttpUtil.getJson(apiUrl);

        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return null;
        }

        if (jsonResponse.contains("\"slim\":")) {
            int start = jsonResponse.indexOf("\"slim\":\"") + 8;
            int end = jsonResponse.indexOf("\"", start);
            return jsonResponse.substring(start, end);
        } else if (jsonResponse.contains("\"default\":")) {
            int start = jsonResponse.indexOf("\"default\":\"") + 11;
            int end = jsonResponse.indexOf("\"", start);
            return jsonResponse.substring(start, end);
        }

        return null;
    }

    private void applySkinFromTextureId(ProxiedPlayer player, String textureId) throws Exception {
        String textureUrl = String.format(TEXTURE_URL, textureId);
        BufferedImage skinImage = ImageIO.read(new URL(textureUrl));

        if (skinImage == null) {
            throw new IOException("无法下载皮肤png文件");
        }

        int width = skinImage.getWidth();
        int height = skinImage.getHeight();
        if (!((width == 64 && height == 64) || (width == 64 && height == 32))) {
            throw new IOException("非法皮肤尺寸: " + width + "x" + height);
        }

        String[] skinData = uploadSkinToMineSkin(textureUrl);
        if (skinData == null || skinData.length < 2) {
            throw new IOException("无法上传皮肤到MineSkin");
        }

        String value = skinData[0];
        String signature = skinData[1];

        skinStorage.setCustomSkinData(player.getName(), SkinProperty.of(value, signature));
        Optional<InputDataResult> result = skinStorage.findOrCreateSkinData(player.getName());

        if (!result.isPresent()) {
            throw new IOException("无法创建皮肤数据");
        }

        playerStorage.setSkinIdOfPlayer(player.getUniqueId(), result.get().getIdentifier());
        skinsRestorerAPI.getSkinApplier(ProxiedPlayer.class).applySkin(player);

        getLogger().info(player.getName() + "的皮肤已更新");
        player.sendMessage(new TextComponent("§8[§b皮肤系统§8] §l>>>>>> \n §a你的皮肤已在HelloSkin更新, 新皮肤将在切换区服或重进服务器时生效"));
    }

    private String[] uploadSkinToMineSkin(String skinUrl) throws Exception {
        String uploadUrl = "https://api.mineskin.org/generate/url";
        String jsonResponse = HttpUtil.postJson(uploadUrl, "{\"url\":\"" + skinUrl + "\"}");

        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return null;
        }

        int valueStart = jsonResponse.indexOf("\"value\":\"") + 9;
        int valueEnd = jsonResponse.indexOf("\"", valueStart);
        String value = jsonResponse.substring(valueStart, valueEnd);

        int signatureStart = jsonResponse.indexOf("\"signature\":\"") + 13;
        int signatureEnd = jsonResponse.indexOf("\"", signatureStart);
        String signature = jsonResponse.substring(signatureStart, signatureEnd);

        return new String[]{value, signature};
    }
}