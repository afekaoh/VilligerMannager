package mc.adam.vg;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.WritableBookMeta;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VillagerLogCommand implements CommandExecutor {
    private final VillagerManager plugin;
    private final StringBuilder cachedVillagerLog = new StringBuilder();

    public VillagerLogCommand(VillagerManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be run by a player.");
            return true;
        }

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "setLectern":
                    Location loc = player.getTargetBlockExact(5).getLocation();
                    plugin.setLecternLocation(loc);
                    player.sendMessage("Lectern location set and saved.");
                    return true;
                case "book":
                    updateLecternIfChunkLoaded();
                    player.sendMessage("Lectern book updated.");
                    return true;
                case "sync":
                    int count = plugin.syncNamedVillagers();
                    updateLecternIfChunkLoaded();
                    player.sendMessage("Synchronized " + count + " villagers.");
                    return true;
                case "awake":
                    String awakeVillagers = plugin.getAwakeOrAsleepVillagers(false);
                    player.sendMessage("--- awake villagers ---");
                    player.sendMessage(awakeVillagers);
                    return true;
                case "asleep":
                    String asleepVillagers = plugin.getAwakeOrAsleepVillagers(true);
                    player.sendMessage("--- sleeping villagers ---");
                    player.sendMessage(asleepVillagers);
                    return true;
                case "log":
                    String log = getVillagerLog();
                    player.sendMessage("--- Villager Log ---");
                    for (String line : log.split("\\n")) {
                        player.sendMessage(line);
                    }
                    return true;
                default:
                    player.sendMessage("Unknown command: " + args[0]);
                    player.sendMessage("Usage: /villagerlog [ |log|setLectern|book|sync|awake|asleep]");
                    break;
            }
        } else {
            String log = getVillagerLog();
            player.sendMessage("--- Villager Log ---");
            for (String line : log.split("\\n")) {
                player.sendMessage(line);
            }
            return true;
        }
        player.sendMessage("Usage: /villagerlog [setLectern|book|sync|awake|asleep]");
        return true;
    }

    private void appendToVillagerLog(String line) {
        cachedVillagerLog.append(line).append("\n");
    }

    private void resetVillagerLog() {
        cachedVillagerLog.setLength(0);
    }

    public String getVillagerLog() {
        return cachedVillagerLog.toString();
    }

    public void fillLog() {
        resetVillagerLog();

        List<VillagerRecord> villagers = new ArrayList<>(plugin.trackedVillagers.values());
        villagers.sort((a, b) -> {
            if (a.isAlive != b.isAlive)
                return a.isAlive ? -1 : 1;
            return naturalCompare(a.getDisplayName(), b.getDisplayName());
        });

        for (VillagerRecord record : villagers) {
            String name = record.getDisplayName() != null ? record.getDisplayName() : "Unknown";
            var job = record.job != null && !record.job.toString().isBlank() ? record.job : "Unemployed";
            String status = record.isAlive ? "Alive" : "DEAD";

            appendToVillagerLog(String.format("§lName§r: %s, §lJob§r: %s, §lStatus§r: %s", name, job, status));
        }
    }

    public void updateLecternIfChunkLoaded() {
        if (!plugin.isLacternChunkLoaded())
            return;
        var lectern = plugin.getLectern();
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        WritableBookMeta writeMeta = (WritableBookMeta) book.getItemMeta();

        List<String> pages = createBookPages();

        writeMeta.setPages(pages);
        var meta = (BookMeta) writeMeta;
        meta.setTitle("Villager Log");
        meta.setAuthor("AutoManager");
        book.setItemMeta(meta);

        lectern.getSnapshotInventory().setItem(0, book);
        lectern.update();
    }

    public List<String> createBookPages() {
        List<String> pages = new ArrayList<>();
        Map<UUID, VillagerRecord> records = plugin.trackedVillagers;
        List<UUID> sorted = new ArrayList<>(records.keySet());

        // Sort: alive first, then by name with numeric awareness
        sorted.sort((a, b) -> {
            VillagerRecord va = records.get(a), vb = records.get(b);
            if (va.isAlive != vb.isAlive)
                return Boolean.compare(vb.isAlive, va.isAlive);
            return naturalCompare(va.getDisplayName(), vb.getDisplayName());
        });

        // Page 1: Overview
        var alive = sorted.stream().filter(id -> records.get(id).isAlive).count();
        var dead = sorted.size() - alive;
        var total = sorted.size();
        pages.add("§lVillager Registry§r\n\n" +
                "Total: " + total + "\n" +
                "Alive: " + alive + "\n" +
                "Dead: " + dead + "\n" +
                "Updated: " + LocalDateTime.now());

        // Placeholder index (to be filled later)
        final int NUM_OF_LINES = 14;
        var indexPage = new StringBuilder("§lVillager Index§r\n\n");
        var linesUsed = 2; // 2 lines for header
        var numberOfPages = total / (NUM_OF_LINES - linesUsed) + 1;
        List<String> indexLines = new ArrayList<>();

        // Pages 3+: Detailed pages
        var currentPage = 1 + numberOfPages; // 0-based index, page 3 is at index 2
        var villagerTemp = 1;
        for (var id : sorted) {
            var stats = plugin.getVillagerStats(id); // Provided by plugin
            if (stats == null || stats.isEmpty() || stats.get("error") != null) {
                continue; // Skip if no stats or error
            }
            var name = stats.get("name") != null ? stats.get("name") : "Unnamed Villager" + villagerTemp++;

            var page = new StringBuilder();
            page.append("§l").append(name).append("§r\n");

            for (var entry : stats.entrySet()) {
                var key = entry.getKey().toUpperCase();
                if (key.equalsIgnoreCase("name"))
                    continue; // Skip name as it's already displayed
                var value = entry.getValue() != null ? entry.getValue() : "Unknown";
                page.append(key).append(": ").append(value).append("\n");
            }

            indexLines.add(getNiceName(name) + " .... Pg " + (currentPage + 1));
            pages.add(trimPage(page));
            currentPage++;
        }

        // Page 2: Index
        var startingPage = 1;
        for (var i = 0; i < indexLines.size(); i++) {
            for (var j = 0; j < NUM_OF_LINES - linesUsed; j++) {
                if (i >= indexLines.size()) {
                    break;
                }
                String line = indexLines.get(i);
                indexPage.append(line).append("\n");
                i++;
            }
            // add all the lines to the index page
            pages.add(startingPage, indexPage.toString());
            startingPage++;
            if (i >= indexLines.size()) {
                break; // No more lines to add
            }
            // If we have more lines to add, create a new page
            indexPage.setLength(0); // Reset for next page
            indexPage.append("§lVillager Index Cont...§r\n\n");
        }

        return pages;
    }

    /**
     * @param page (StringBuilder) - The page to be trimmed
     * @return (String) - The trimmed page not exceeding 255 characters
     */
    private String trimPage(StringBuilder page) {
        var raw = page.toString();
        return raw.length() <= 255 ? raw : raw.substring(0, 255);
    }

    private String getNiceName(String name) {
        if (name == null || name.isBlank()) {
            return "Unnamed Villager";
        }
        var parts = name.split(" ");
        var niceName = new StringBuilder();
        niceName.append(name.substring(0, 7).toUpperCase());
        if (parts.length > 1) {
            niceName.append(" ").append(parts[1]);
        }
        return niceName.toString();
    }

    private int naturalCompare(String a, String b) {
        // Compare alphabetically with numeric awareness (e.g. Bob2 < Bob10)
        return Comparator
                .comparing((String s) -> s.replaceAll("\\d+", ""))
                .thenComparingInt(s -> {
                    Matcher m = Pattern.compile("\\d+").matcher(s);
                    return m.find() ? Integer.parseInt(m.group()) : 0;
                }).compare(a, b);
    }

}
