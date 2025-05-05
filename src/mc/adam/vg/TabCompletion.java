package mc.adam.vg;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TabCompletion implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if ((cmd.getName().equalsIgnoreCase("vlog")
                || cmd.getName().equalsIgnoreCase("villagerlog"))
                && args.length >= 0) {
            if (sender instanceof Player player) {
                if (args.length == 1) {
                    List<String> list = new ArrayList<>();
                    list.add("setLectern");
                    list.add("book");
                    list.add("sync");
                    list.add("awake");
                    list.add("asleep");
                    list.add("log");
                    return list;
                }

            }
        }
        return null;
    }
}