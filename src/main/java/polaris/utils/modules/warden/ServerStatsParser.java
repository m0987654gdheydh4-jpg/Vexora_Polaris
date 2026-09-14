package polaris.utils.modules.warden;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import polaris.mixin.accessor.BossHealthOverlayAccessor;
import polaris.utils.network.Network;


public final class ServerStatsParser {
    public static final ServerStatsParser INSTANCE = new ServerStatsParser();

    private static final Pattern PATTERN = Pattern.compile("(?iu)(?:анарх(?:ия|ии)?|anarchy|an)\\s*[-:#№]?\\s*(\\d{1,5})");
    private static final Pattern PATTERN_2 = Pattern.compile("([a-zA-Z0-9_]{3,16})");
    private static final Pattern PATTERN_3 = Pattern.compile("Монет:\\s*(.+)");
    private static final Pattern PATTERN_4 = Pattern.compile("Токенов:\\s*(\\d+)");
    private static final Pattern PATTERN_5 = Pattern.compile("Ранг:\\s*(.+)");
    private static final Pattern PATTERN_6 = Pattern.compile("Убийств:\\s*(\\d+)");
    private static final Pattern PATTERN_7 = Pattern.compile("Смертей:\\s*(\\d+)");
    private static final Pattern PATTERN_8 = Pattern.compile("Наиграно:\\s*(.+)");

    public static String nA = "N/A";
    private String nA2 = "N/A";
    private String nA3 = "N/A";
    private String nA4 = "N/A";
    private String text0 = "0";
    private String text02 = "0";
    private String text03 = "0";
    private String text04 = "0";
    private String text05 = "0";
    private long timestamp;

    private ServerStatsParser() {
    }

    public void invoke(long l) {
        long now = System.currentTimeMillis();
        if (now - this.timestamp >= l) {
            this.timestamp = now;
            this.invoke2();
        }
    }

    public void invoke2() {
        Minecraft client = Minecraft.getInstance();
        this.nA2 = "N/A";
        this.text0 = "0";
        this.text02 = "0";
        this.text03 = "0";
        this.text04 = "0";
        this.text05 = "0";

        
        try {
            int an = Network.getAnarchyMode();
            if (an > 0) {
                this.nA2 = Integer.toString(an);
            }
        } catch (Throwable ignored) {
        }

        if (client.level == null || client.player == null) {
            return;
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return;
        }

        String header = objective.getDisplayName().getString();
        Matcher matcher = PATTERN.matcher(strip(header));
        if (matcher.find()) {
            this.nA2 = matcher.group(1);
        }

        List<String> lines = resolve(scoreboard, objective);
        for (int i = 0; i < lines.size(); i++) {
            String text2 = lines.get(i);
            String text3 = strip(text2);

            if ("N/A".equals(this.nA2)) {
                Matcher m2 = PATTERN.matcher(text3);
                if (m2.find()) {
                    this.nA2 = m2.group(1);
                }
            }

            if (i < 5 && !text3.contains(":") && !text3.contains("=") && !text3.trim().isEmpty()) {
                Matcher m3 = PATTERN_2.matcher(text3);
                if (m3.find()) {
                    this.nA3 = m3.group(1);
                    nA = this.nA3;
                }
            }

            Matcher mRank = PATTERN_5.matcher(text3);
            if (mRank.find()) {
                this.nA4 = mRank.group(1).trim();
            }

            Matcher mCoins = PATTERN_3.matcher(text3);
            if (mCoins.find()) {
                this.text0 = mCoins.group(1).replaceAll("[^0-9]", "");
            }

            Matcher mTok = PATTERN_4.matcher(text3);
            if (mTok.find()) {
                this.text02 = mTok.group(1);
            }

            Matcher mKill = PATTERN_6.matcher(text3);
            if (mKill.find()) {
                this.text03 = mKill.group(1);
            }

            Matcher mDeath = PATTERN_7.matcher(text3);
            if (mDeath.find()) {
                this.text04 = mDeath.group(1);
            }

            Matcher mPlay = PATTERN_8.matcher(text3);
            if (mPlay.find()) {
                this.text05 = mPlay.group(1);
            }
        }
    }

    private List<String> resolve(Scoreboard scoreboard, Objective objective) {
        ArrayList<String> out = new ArrayList<>();
        Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective);
        ArrayList<PlayerScoreEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
        int limit = Math.min(sorted.size(), 15);
        for (int i = 0; i < limit; i++) {
            PlayerScoreEntry entry = sorted.get(i);
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            out.add(PlayerTeam.formatNameForTeam(team, Component.literal(entry.owner())).getString());
        }
        return out;
    }

    private String strip(String string) {
        return string == null ? "" : string.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
    }

    public static boolean check() {
        return Network.isPvp();
    }

    public String getNA2() {
        return this.nA2;
    }

    public String getNA3() {
        return this.nA3;
    }

    public String getNA4() {
        return this.nA4;
    }

    public String getText0() {
        return this.text0;
    }

    public String getText02() {
        return this.text02;
    }

    public String getText03() {
        return this.text03;
    }

    public String getText04() {
        return this.text04;
    }

    public String getText05() {
        return this.text05;
    }

    public long getTimestamp() {
        return this.timestamp;
    }
}
