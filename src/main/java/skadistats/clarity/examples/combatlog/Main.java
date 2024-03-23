// *****  ALL IDEAS  ***** \\
// where faceless hits all his allies with chrono and then dies right after
// where faceless void hits 0 enemies with chrono

package skadistats.clarity.examples.combatlog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import skadistats.clarity.Clarity;
import skadistats.clarity.model.CombatLogEntry;
import skadistats.clarity.processor.gameevents.OnCombatLogEntry;
import skadistats.clarity.processor.runner.SimpleRunner;
import skadistats.clarity.source.MappedFileSource;
import skadistats.clarity.wire.shared.demo.proto.Demo.CDemoFileInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Main {

    private final Logger log = LoggerFactory.getLogger(Main.class.getPackage().getClass());

    private final DateTimeFormatter GAMETIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private String compileName(String attackerName, boolean isIllusion) {
        return attackerName != null ? attackerName + (isIllusion ? " (illusion)" : "") : "UNKNOWN";
    }

    private String getAttackerNameCompiled(CombatLogEntry cle) {
        return compileName(cle.getAttackerName(), cle.isAttackerIllusion());
    }

    private String getTargetNameCompiled(CombatLogEntry cle) {
        return compileName(cle.getTargetName(), cle.isTargetIllusion());
    }

    private Map<String, SpellInfo> spellInfoMap = new HashMap<>();
    private Map<String, Integer> heroTeamMap = new HashMap<>();

    private static class SpellInfo {
        List<String> casts = new ArrayList<>();
        List<Map<String, String>> targets = new ArrayList<>();

        @Override
        public String toString() {
            return "SpellInfo{casts=" + casts + ", targets=" + targets + "}";
        }
    }

    @OnCombatLogEntry
    public void onCombatLogEntry(CombatLogEntry cle) {
        Duration gameTimeMillis = Duration.ofMillis((int) (1000.0f * cle.getTimestamp()));
        LocalTime gameTime = LocalTime.MIDNIGHT.plus(gameTimeMillis);
        String time = "[" + GAMETIME_FORMATTER.format(gameTime) + "]";
        switch (cle.getType()) {
            case DOTA_COMBATLOG_DAMAGE:
                if (getTargetNameCompiled(cle).contains("npc_dota_hero")
                        && !getTargetNameCompiled(cle).contains("illusion")) {

                    if ("earthshaker_echo_slam".equals(cle.getInflictorName())
                            || "tidehunter_ravage".equals(cle.getInflictorName())
                            || "magnataur_reverse_polarity".equals(cle.getInflictorName())) {

                        SpellInfo spellInfo = spellInfoMap.get(cle.getInflictorName());
                        if (spellInfo == null) {
                            spellInfo = new SpellInfo();
                            spellInfoMap.put(cle.getInflictorName(), spellInfo);
                        }

                        Map<String, String> target = new HashMap<>();
                        target.put(getTargetNameCompiled(cle), String.valueOf(cle.getTimestamp()));
                        spellInfo.targets.add(target);

                        // log.info("{} {} hits {}{} for {} damage{}",
                        // cle.getTimestamp(),
                        // getAttackerNameCompiled(cle),
                        // getTargetNameCompiled(cle),
                        // cle.getInflictorName() != null ? String.format(" with %s",
                        // cle.getInflictorName()) : "",
                        // cle.getValue(),
                        // cle.getHealth() != 0
                        // ? String.format(" (%s->%s)", cle.getHealth() + cle.getValue(),
                        // cle.getHealth())
                        // : "");
                    }
                }
                break;
            case DOTA_COMBATLOG_MODIFIER_ADD:

                if (getTargetNameCompiled(cle).contains("npc_dota_hero")
                        && !getTargetNameCompiled(cle).contains("illusion")) {
                    if ("modifier_faceless_void_chronosphere_freeze".equals(cle.getInflictorName())) {
                        SpellInfo spellInfo = spellInfoMap.get("faceless_void_chronosphere");
                        if (spellInfo == null) {
                            spellInfo = new SpellInfo();
                            spellInfoMap.put("faceless_void_chronosphere", spellInfo);
                        }

                        Map<String, String> target = new HashMap<>();
                        target.put(getTargetNameCompiled(cle), String.valueOf(cle.getTimestamp()));
                        spellInfo.targets.add(target);

                        log.info("{} {} receives {} buff/debuff from {}",
                                time,
                                getTargetNameCompiled(cle),
                                "faceless_void_chronosphere",
                                getAttackerNameCompiled(cle));
                    }
                }
                break;
            // case DOTA_COMBATLOG_MODIFIER_REMOVE:
            // log.info("{} {} loses {} buff/debuff",
            // time,
            // getTargetNameCompiled(cle),
            // cle.getInflictorName());
            // break;
            case DOTA_COMBATLOG_ABILITY:
                if ("earthshaker_echo_slam".equals(cle.getInflictorName()) ||
                        "tidehunter_ravage".equals(cle.getInflictorName()) ||
                        "faceless_void_chronosphere".equals(cle.getInflictorName()) ||
                        "magnataur_reverse_polarity".equals(cle.getInflictorName())) {
                    SpellInfo spellInfo = spellInfoMap.get(cle.getInflictorName());
                    if (spellInfo == null) {
                        spellInfo = new SpellInfo();
                        spellInfoMap.put(cle.getInflictorName(), spellInfo);
                    }

                    // Append the timestamp to echo casts
                    spellInfo.casts.add(String.valueOf(cle.getTimestamp()));

                    // log.info("{} {} {} ability {} (lvl {}){}{}",
                    // cle.getTimestamp(),
                    // getAttackerNameCompiled(cle),
                    // cle.isAbilityToggleOn() || cle.isAbilityToggleOff() ? "toggles" : "casts",
                    // cle.getInflictorName(),
                    // cle.getAbilityLevel(),
                    // cle.isAbilityToggleOn() ? " on" : cle.isAbilityToggleOff() ? " off" : "",
                    // cle.getTargetName() != null ? " on " + getTargetNameCompiled(cle) : "",
                    // cle.getAttackerName() != null ? "" : "");
                }
                break;

        }
    }

    public void run(String[] args) throws Exception {
        CDemoFileInfo info = Clarity.infoForFile(args[0]);
        Gson gson = new Gson();
        String json = gson.toJson(info);

        JsonElement jsonTree = JsonParser.parseString(json);
        JsonObject jsonObject = jsonTree.getAsJsonObject();
        JsonObject gameInfo = jsonObject.getAsJsonObject("gameInfo_");

        JsonObject dota = gameInfo.getAsJsonObject("dota_");
        long matchId = dota.getAsJsonPrimitive("matchId_").getAsLong();

        JsonArray playerInfoArray = dota.getAsJsonArray("playerInfo_");
        for (JsonElement playerInfoElement : playerInfoArray) {
            JsonObject playerInfoObject = playerInfoElement.getAsJsonObject();
            int gameTeam = playerInfoObject.get("gameTeam_").getAsInt();
            String heroName = "";

            JsonElement heroNameElement = playerInfoObject.get("heroName_");
            if (heroNameElement.isJsonObject()) {
                JsonArray bytesArray = heroNameElement.getAsJsonObject().getAsJsonArray("bytes");
                byte[] bytes = new byte[bytesArray.size()];
                for (int i = 0; i < bytesArray.size(); i++) {
                    bytes[i] = bytesArray.get(i).getAsByte();
                }
                heroName = new String(bytes, StandardCharsets.UTF_8);
            } else if (heroNameElement.isJsonPrimitive()) {
                heroName = heroNameElement.getAsString();
            }

            heroTeamMap.put(heroName, gameTeam);

            System.out.println(heroName);
            System.out.println(gameTeam);

        }

        String csvFilePath = "test.csv";
        long tStart = System.currentTimeMillis();
        new SimpleRunner(new MappedFileSource(args[0])).runWith(this);
        long tMatch = System.currentTimeMillis() - tStart;

        try (FileWriter fileWriter = new FileWriter(csvFilePath, true)) {
            json = gson.toJson(spellInfoMap);

            try (FileWriter writer = new FileWriter("map.json")) {
                writer.write(json);
            } catch (IOException e) {
                e.printStackTrace();
            }
            for (Map.Entry<String, SpellInfo> entry : spellInfoMap.entrySet()) {
                System.out.println("Key: " + entry.getKey());
                SpellInfo spellInfo = entry.getValue();
                for (String cast : spellInfo.casts) {
                    Integer targetCount = 0;
                    float castFloat = Float.parseFloat(cast);
                    System.out.println("cast time: " + castFloat);
                    for (Map<String, String> target : spellInfo.targets) {
                        for (Map.Entry<String, String> targetEntry : target.entrySet()) {
                            String targetName = targetEntry.getKey();
                            float targetTimeFloat = Float.parseFloat(targetEntry.getValue());
                            if (targetTimeFloat >= castFloat && targetTimeFloat < castFloat + 5) {
                                System.out.println("target time: " + targetEntry.getValue());
                                System.out.println("traget name: " + targetName);
                                if (entry.getKey().equals("faceless_void_chronosphere")) {
                                    Integer facelessVoidTeam = heroTeamMap.get("npc_dota_hero_faceless_void");
                                    Integer targetNameTeam = heroTeamMap.get(targetName);
                                    if (!facelessVoidTeam.equals(targetNameTeam)) {
                                        targetCount += 1;
                                    }
                                    System.out.println("targetCount");
                                    System.out.println(targetCount);
                                } else {
                                    targetCount += 1;
                                }
                            }
                        }
                    }

                    System.out.println("total targets hit: ");
                    System.out.println(targetCount);
                    if (targetCount == 0) {
                        System.out.println("MISSED LOL");
                        fileWriter.append(String.valueOf(matchId));
                        fileWriter.append(",");
                        fileWriter.append(entry.getKey());
                        fileWriter.append(",");
                        fileWriter.append(Float.toString(castFloat));
                        fileWriter.append(",");
                        fileWriter.append("Missed");
                        fileWriter.append("\n");
                    }
                    System.out.println("\n\n\nnext cast:");
                }
            }
        } catch (IOException e) {
            System.out.println("An error occurred while writing to the CSV file");
            e.printStackTrace();
        }

        log.info("total time taken: {}s", (tMatch) / 1000.0);
    }

    public static void main(String[] args) throws Exception {
        new Main().run(args);
    }

}
