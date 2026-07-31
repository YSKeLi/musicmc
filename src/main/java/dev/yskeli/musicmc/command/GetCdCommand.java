package dev.yskeli.musicmc.command;

import dev.yskeli.musicmc.disc.DiscService;
import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongNames;
import dev.yskeli.musicmc.resource.ResourcePackService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GetCdCommand implements CommandExecutor, TabCompleter {
    private final SongLibrary library;
    private final DiscService discs;
    private final ResourcePackService resourcePacks;

    public GetCdCommand(SongLibrary library, DiscService discs, ResourcePackService resourcePacks) {
        this.library = library;
        this.discs = discs;
        this.resourcePacks = resourcePacks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "这个命令只能由玩家执行。");
            return true;
        }
        if (!player.hasPermission("musicmc.getcd")) {
            player.sendMessage(ChatColor.RED + "你没有获取唱片的权限。");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "用法: /getcd <歌曲命令名称>");
            return true;
        }
        String requestedName = String.join(" ", args);
        Song song = library.findByName(requestedName).orElse(null);
        if (song == null) {
            player.sendMessage(ChatColor.RED + "没有找到歌曲“" + requestedName + "”，可使用 /music 查看。");
            return true;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(discs.createDisc(song));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        resourcePacks.sendSong(player, song);
        player.sendMessage(ChatColor.GREEN + "已获得唱片: " + ChatColor.AQUA + song.displayLabel());
        if (!resourcePacks.hasLoaded(player, song)) {
            player.sendMessage(ChatColor.YELLOW + "资源包正在加载；完成后再把唱片放入唱片机。");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("musicmc.getcd")) return List.of();
        String entered = String.join(" ", args);
        String normalized = SongNames.normalize(entered);
        int lastSpace = entered.lastIndexOf(' ');
        return library.songs().stream().map(Song::commandName)
                .filter(name -> SongNames.normalize(name).startsWith(normalized))
                .sorted(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)))
                .map(name -> lastSpace < 0 ? name : name.substring(Math.min(lastSpace + 1, name.length())))
                .distinct().toList();
    }
}
