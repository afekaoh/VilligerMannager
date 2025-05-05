package mc.adam.vg;

import org.bukkit.entity.Villager;
import java.util.UUID;

public class VillagerRecord {
    public final UUID uuid;
    public String name;
    public Villager.Profession job;
    public boolean isAlive;

    public VillagerRecord(UUID uuid, String name, Villager.Profession job, boolean isAlive) {
        this.uuid = uuid;
        this.name = name;
        this.job = job;
        this.isAlive = isAlive;
    }

    public VillagerRecord(UUID uuid, String name, Villager.Profession job) {
        this.uuid = uuid;
        this.name = name;
        this.job = job;
        this.isAlive = true;
    }

    public String getDisplayName() {
        return name != null ? name : "(unnamed)";
    }
}