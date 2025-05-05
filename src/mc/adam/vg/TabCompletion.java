package mc.adam.vg;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TabCompletion implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if ((cmd.getName().equalsIgnoreCase("vlog") || cmd.getName().equalsIgnoreCase("villagerlog")))
            if (sender instanceof Player)
                return List.of("log", "setLectern", "book", "sync", "awake", "asleep");
        return null;
    }
}