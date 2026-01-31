package fun.eqad.eskin;

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
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class SkinManager {

    private static final String SKIN_API_URL = "https://helloskin.cn/%s.json";
    private static final String TEXTURE_URL = "https://helloskin.cn/textures/%s";

    private SkinsRestorer skinsRestorerAPI;
    private SkinStorage skinStorage;
    private PlayerStorage playerStorage;
    private final Logger logger;

    public SkinManager(Logger logger) {
        this.logger = logger;
    }

    private void ensureApiInitialized() {
        if (this.skinsRestorerAPI == null) {
            this.skinsRestorerAPI = SkinsRestorerProvider.get();
            this.skinStorage = skinsRestorerAPI.getSkinStorage();
            this.playerStorage = skinsRestorerAPI.getPlayerStorage();
        }
    }

    public String getPlayerTextureId(String playerName) throws IOException {
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

    public void applySkinFromTextureId(UUID playerUUID, String playerName, String textureId) throws Exception {
        ensureApiInitialized();
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

        skinStorage.setCustomSkinData(playerName, SkinProperty.of(value, signature));
        Optional<InputDataResult> result = skinStorage.findOrCreateSkinData(playerName);

        if (!result.isPresent()) {
            throw new IOException("无法创建皮肤数据");
        }

        playerStorage.setSkinIdOfPlayer(playerUUID, result.get().getIdentifier());

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
