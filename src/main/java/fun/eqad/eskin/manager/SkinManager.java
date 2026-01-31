package fun.eqad.eskin.manager;

import fun.eqad.eskin.util.HttpUtil;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.property.SkinProperty;
import net.skinsrestorer.api.storage.PlayerStorage;
import net.skinsrestorer.api.storage.SkinStorage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class SkinManager {

    public static class SkinServer {
        public String name;
        public String csl;
        public String texture;

        public SkinServer(String name, String csl, String texture) {
            this.name = name;
            this.csl = csl;
            this.texture = texture;
        }
    }

    public static class SkinSearchResult {
        public String textureId;
        public SkinServer server;

        public SkinSearchResult(String textureId, SkinServer server) {
            this.textureId = textureId;
            this.server = server;
        }
    }

    private List<SkinServer> skinServers = new ArrayList<>();
    private SkinsRestorer skinsRestorerAPI;
    private SkinStorage skinStorage;
    private PlayerStorage playerStorage;
    private final Logger logger;

    public SkinManager(Logger logger) {
        this.logger = logger;
    }

    public void addSkinServer(String name, String csl, String texture) {
        skinServers.add(new SkinServer(name, csl, texture));
    }

    public void clearSkinServers() {
        skinServers.clear();
    }

    private void ensureApiInitialized() {
        if (this.skinsRestorerAPI == null) {
            this.skinsRestorerAPI = SkinsRestorerProvider.get();
            this.skinStorage = skinsRestorerAPI.getSkinStorage();
            this.playerStorage = skinsRestorerAPI.getPlayerStorage();
        }
    }

    public SkinSearchResult getPlayerTextureId(String playerName) {
        for (SkinServer server : skinServers) {
            try {
                String apiUrl = server.csl.replace("%player%", playerName);
                String jsonResponse = HttpUtil.getJson(apiUrl);

                if (jsonResponse == null || jsonResponse.isEmpty()) {
                    continue;
                }

                String textureId = null;
                if (jsonResponse.contains("\"slim\":")) {
                    int start = jsonResponse.indexOf("\"slim\":\"") + 8;
                    int end = jsonResponse.indexOf("\"", start);
                    textureId = jsonResponse.substring(start, end);
                } else if (jsonResponse.contains("\"default\":")) {
                    int start = jsonResponse.indexOf("\"default\":\"") + 11;
                    int end = jsonResponse.indexOf("\"", start);
                    textureId = jsonResponse.substring(start, end);
                }

                if (textureId != null) {
                    return new SkinSearchResult(textureId, server);
                }
            } catch (Exception e) {
                logger.warning("从 " + server.name + " 获取皮肤失败: " + e.getMessage());
            }
        }
        return null;
    }

    public void applySkinFromTextureId(UUID playerUUID, String playerName, SkinSearchResult result) throws Exception {
        ensureApiInitialized();
        String textureUrl = result.server.texture.replace("%texture%", result.textureId);
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

        skinStorage.setCustomSkinData(playerName, SkinProperty.of(value, signature));
        Optional<InputDataResult> skinDataResult = skinStorage.findOrCreateSkinData(playerName);

        if (!skinDataResult.isPresent()) {
            throw new IOException("无法创建皮肤数据");
        }

        playerStorage.setSkinIdOfPlayer(playerUUID, skinDataResult.get().getIdentifier());

        logger.info(playerName + "的皮肤数据已更新");
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
