package mc.adam.vg;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

public class VillagerTrackerListener implements Listener {
    private final VillagerManager plugin;

    public VillagerTrackerListener(VillagerManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVillagerBreed(EntityBreedEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            VillagerRecord recordFather = plugin.trackedVillagers.get(event.getFather().getUniqueId());
            VillagerRecord recordMother = plugin.trackedVillagers.get(event.getMother().getUniqueId());
            if (recordFather != null || recordMother != null) {
                VillagerRecord newRecord = new VillagerRecord(villager.getUniqueId(), null, null);
                plugin.trackedVillagers.put(villager.getUniqueId(), newRecord);
                plugin.getLogger().info("New villager born: " + newRecord.getDisplayName());
                plugin.updateLog();
            }
        }
    }

    @EventHandler
    public void onVillagerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            VillagerRecord record = plugin.trackedVillagers.get(villager.getUniqueId());
            if (record != null) {
                record.isAlive = false;
                plugin.updateLog();
            }
        }
    }

    @EventHandler
    public void onVillagerRename(PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if (e instanceof Villager villager) {
            VillagerRecord record = plugin.trackedVillagers.get(villager.getUniqueId());
            if (record != null
                    && (villager.getCustomName() != null && villager.getCustomName() != record.getDisplayName())) {
                record.name = villager.getCustomName();
                plugin.updateLog();
            }
        }
    }

    @EventHandler
    public void onVillagerJobChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        VillagerRecord record = plugin.trackedVillagers.get(villager.getUniqueId());
        if (record != null) {
            record.job = event.getProfession();
        }
    }
}
