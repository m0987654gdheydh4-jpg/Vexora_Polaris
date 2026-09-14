package polaris;

import net.fabricmc.loader.impl.launch.knot.KnotClient;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Start {
    public static void main(String[] args) {
        File runDir = new File("run");
        if (!runDir.exists()) {
            runDir.mkdirs();
        }

        // Ищем системную папку .gradle в корне проекта, где хранятся скачанные ассеты Minecraft
        File gradleAssets = new File(System.getProperty("user.home"), ".gradle/caches/fabric-loom/assets");

        // Если Gradle их не скачал, пробуем стандартный путь .minecraft
        File systemMinecraft = new File(System.getProperty("user.home"), "AppData/Roaming/.minecraft/assets");

        String assetsPath = gradleAssets.exists() ? gradleAssets.getAbsolutePath() :
                (systemMinecraft.exists() ? systemMinecraft.getAbsolutePath() : new File(runDir, "assets").getAbsolutePath());

        List<String> argumentList = new ArrayList<>(Arrays.asList(
                "--version", "1.21.11",
                "--gameDir", runDir.getAbsolutePath(),   // Все конфиги и миры остаются строго в папке run
                "--assetsDir", assetsPath,               // Путь к языковым пакетам
                "--assetIndex", "1.21",                  // Индекс для версий 1.21.x
                "--uuid", "00000000-0000-0000-0000-000000000000",
                "--accessToken", "0"
        ));

        argumentList.addAll(Arrays.asList(args));

        System.setProperty("fabric.development", "true");
        System.setProperty("log4j.configurationFile", "log4j2.xml");

        KnotClient.main(argumentList.toArray(new String[0]));
    }
}
