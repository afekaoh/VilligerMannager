package mc.adam.vg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

public class VillagerManager extends JavaPlugin {
    public final Map<UUID, VillagerRecord> trackedVillagers = new HashMap<>();
    private File villagersFile;
    private FileConfiguration villagersConfig;
    private Location lecternLocation;
    private final VillagerLogCommand villagerLogCommand = new VillagerLogCommand(this);

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new VillagerTrackerListener(this), this);
        setupFiles();
        loadVillagers();
        loadLecternLocation();
        getCommand("villagerlog").setExecutor(villagerLogCommand);
        getCommand("villagerlog").setTabCompleter(new TabCompletion());
        villagerLogCommand.fillLog();
        getLogger().info("Villager Manager Plugin enabled.");
    }

    @Override
    public void onDisable() {
        saveVillagers();
        getLogger().info("Villager Manager Plugin disabled.");
    }

    private void setupFiles() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        villagersFile = new File(getDataFolder(), "villagers.yml");
        if (!villagersFile.exists()) {
            try {
                villagersFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        villagersConfig = YamlConfiguration.loadConfiguration(villagersFile);
    }

    public void setLecternLocation(Location location) {
        this.lecternLocation = location;
        saveLecternLocation();
    }

    public Location getLecternLocation() {
        return lecternLocation;
    }

    private void saveLecternLocation() {
        getConfig().set("lectern.world", lecternLocation.getWorld().getName());
        getConfig().set("lectern.x", lecternLocation.getBlockX());
        getConfig().set("lectern.y", lecternLocation.getBlockY());
        getConfig().set("lectern.z", lecternLocation.getBlockZ());
        saveConfig();
    }

    private void loadLecternLocation() {
        if (getConfig().contains("lectern.world")) {
            World world = Bukkit.getWorld(getConfig().getString("lectern.world"));
            int x = getConfig().getInt("lectern.x");
            int y = getConfig().getInt("lectern.y");
            int z = getConfig().getInt("lectern.z");
            lecternLocation = new Location(world, x, y, z);
        }
    }

    private void loadVillagers() {
        if (villagersConfig.contains("villagers")) {
            List<String> villagersStrings = villagersConfig.getStringList("villagers");
            for (String str : villagersStrings) {
                String[] parts = str.split(",");
                if (parts.length == 4) {
                    UUID uuid = UUID.fromString(parts[0]);
                    String name = parts[1];
                    String jobName = parts[2];
                    boolean isAlive = Boolean.parseBoolean(parts[3]);
                    Villager.Profession job = Registry.VILLAGER_PROFESSION.get(NamespacedKey.fromString(jobName));
                    VillagerRecord record = new VillagerRecord(uuid, name, job, isAlive);
                    trackedVillagers.put(uuid, record);
                }
            }
        }
        getLogger().info("Loaded " + trackedVillagers.size() + " villagers from config.");
    }

    private void saveVillagers() {
        List<String> villagersStrings = new ArrayList<>();
        for (VillagerRecord record : trackedVillagers.values()) {
            var uuid = record.uuid.toString();
            var name = record.name;
            var job = "NONE";
            try {
                job = Registry.VILLAGER_PROFESSION.getKey(record.job).asString();
            } catch (IllegalArgumentException e) {
                job = "NONE";
            }
            var isAlive = record.isAlive ? "true" : "false";
            String villagerString = uuid + "," + name + "," + job + "," + isAlive;
            villagersStrings.add(villagerString);
        }
        villagersConfig.set("villagers", villagersStrings);
        try {
            villagersConfig.save(villagersFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isLacternChunkLoaded() {
        if (lecternLocation == null) {
            getLogger().info("Lectern location not set. Skipping update.");
            return false;
        }
        if (!this.lecternLocation.getWorld().isChunkLoaded(lecternLocation.getBlockX() >> 4,
                lecternLocation.getBlockZ() >> 4)) {
            getLogger().info("Lectern chunk not loaded. Skipping update.");
            return false;
        }

        BlockState state = lecternLocation.getBlock().getState();
        if (!(state instanceof Lectern)) {
            getLogger().warning("Lectern not found at expected location.");
            return false;
        }
        return true;
    }

    public Lectern getLectern() {
        if (lecternLocation != null && lecternLocation.getBlock().getState() instanceof Lectern) {
            return (Lectern) lecternLocation.getBlock().getState();
        }
        return null;
    }

    public void updateLog() {
        villagerLogCommand.fillLog();
        villagerLogCommand.updateLecternIfChunkLoaded();

        getLogger().info("Villager log updated.");
    }

    public int syncNamedVillagers() {
        int count = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Villager.class)) {
                if (entity.getCustomName() != null) {
                    Villager villager = (Villager) entity;
                    VillagerRecord record = trackedVillagers.get(villager.getUniqueId());
                    if (record == null) {
                        record = new VillagerRecord(villager.getUniqueId(), villager.getCustomName(),
                                villager.getProfession());
                        trackedVillagers.put(villager.getUniqueId(), record);
                    }
                    count++;
                }
            }
        }
        updateLog();
        return count;
    }

    public Map<String, String> getVillagerStats(UUID id) {
        Map<String, String> stats = new HashMap<>();
        var world = lecternLocation.getWorld();
        var villager = (Villager) world.getEntity(id);
        if (villager == null) {
            stats.put("error", "Villager not found");
            return stats;
        }
        var record = trackedVillagers.get(id);
        var name = record.getDisplayName();
        var profession = record.job;
        var isAlive = record.isAlive ? "Alive" : "Dead";
        var level = villager.getVillagerLevel();
        var age = villager.getTicksLived() / (20 * 60 * 60 * 24);
        var recipeCount = villager.getRecipeCount();
        var health = villager.getHealth();
        var experience = villager.getVillagerExperience();
        stats.put("name", name);
        stats.put("profession", profession.toString());
        stats.put("isAlive", isAlive);
        stats.put("level", String.valueOf(level));
        stats.put("age", String.valueOf(age));
        stats.put("recipeCount", String.valueOf(recipeCount));
        stats.put("health", String.valueOf(health));
        stats.put("experience", String.valueOf(experience));

        return stats;
    }

    public String getAwakeOrAsleepVillagers(boolean isAsleep) {
        StringBuilder awakeVillagers = new StringBuilder();
        var world = lecternLocation.getWorld();
        for (VillagerRecord record : trackedVillagers.values()) {
            var villager = (Villager) world.getEntity(record.uuid);
            if (record.isAlive && villager.isSleeping() == isAsleep) {
                Location loc = villager.getLocation();
                var x = loc.getBlockX();
                var y = loc.getBlockY();
                var z = loc.getBlockZ();
                awakeVillagers.append(record.getDisplayName()).append(": ").append(x).append(", ").append(y)
                        .append(", ").append(z).append("\n");
            }
        }
        if (awakeVillagers.length() == 0) {
            awakeVillagers.append(isAsleep ? " All villagers are awake." : " All villagers are asleep.");
        }
        return awakeVillagers.toString();
    }

}
