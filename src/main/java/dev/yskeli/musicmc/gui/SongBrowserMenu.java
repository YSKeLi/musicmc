package dev.yskeli.musicmc.gui;

import dev.yskeli.musicmc.disc.DiscService;
import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongNames;
import dev.yskeli.musicmc.resource.ResourcePackService;
import dev.yskeli.musicmc.ticket.PendingUploadStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class SongBrowserMenu implements Listener {
    private static final String TITLE_PREFIX = ChatColor.DARK_GREEN + "MusicMC 歌曲 ";
    private static final int PAGE_SIZE = 45;

    private final JavaPlugin plugin;
    private final SongLibrary library;
    private final DiscService discs;
    private final ResourcePackService resourcePacks;
    private final PendingUploadStore pendingUploads;
    private final Map<UUID, View> views = new HashMap<>();
    private final Set<UUID> awaitingSearch = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public SongBrowserMenu(
            JavaPlugin plugin,
            SongLibrary library,
            DiscService discs,
            ResourcePackService resourcePacks,
            PendingUploadStore pendingUploads) {
        this.plugin = plugin;
        this.library = library;
        this.discs = discs;
        this.resourcePacks = resourcePacks;
        this.pendingUploads = pendingUploads;
    }

    public void open(Player player) {
        open(player, new View(0, Filter.ALL, ""));
    }

    public void openSearch(Player player, String query) {
        open(player, new View(0, Filter.SEARCH, query.strip()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !event.getView().getTitle().startsWith(TITLE_PREFIX)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        View view = views.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        List<Song> songs = filteredSongs(player, view);
        int index = view.page() * PAGE_SIZE + event.getRawSlot();
        if (event.getRawSlot() < PAGE_SIZE && index < songs.size()) {
            giveDisc(player, songs.get(index));
            return;
        }
        switch (event.getRawSlot()) {
            case 45 -> open(player, new View(Math.max(0, view.page() - 1), view.filter(), view.query()));
            case 46 -> open(player, new View(0, Filter.ALL, ""));
            case 47 -> open(player, new View(0, Filter.RECENT, ""));
            case 48 -> open(player, new View(0, Filter.MINE, ""));
            case 49 -> {
                awaitingSearch.add(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(ChatColor.AQUA + "请在聊天栏输入歌曲关键词；输入 cancel 可取消。");
            }
            case 53 -> {
                if ((view.page() + 1) * PAGE_SIZE < songs.size()) {
                    open(player, new View(view.page() + 1, view.filter(), view.query()));
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith(TITLE_PREFIX)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().startsWith(TITLE_PREFIX)) {
            views.remove(event.getPlayer().getUniqueId());
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!awaitingSearch.remove(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String query = event.getMessage().strip();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (query.equalsIgnoreCase("cancel") || query.equals("取消")) {
                event.getPlayer().sendMessage(ChatColor.GRAY + "已取消歌曲搜索。");
            } else {
                openSearch(event.getPlayer(), query);
            }
        });
    }

    private void open(Player player, View requested) {
        List<Song> songs = filteredSongs(player, requested);
        int pages = Math.max(1, (songs.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        View view = new View(Math.min(requested.page(), pages - 1), requested.filter(), requested.query());
        Inventory inventory = Bukkit.createInventory(null, 54,
                TITLE_PREFIX + (view.page() + 1) + "/" + pages);
        int start = view.page() * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < songs.size(); slot++) {
            inventory.setItem(slot, discs.createDisc(songs.get(start + slot)));
        }
        inventory.setItem(45, control(Material.ARROW, "上一页"));
        inventory.setItem(46, control(Material.BOOK, "全部歌曲"));
        inventory.setItem(47, control(Material.CLOCK, "最近上传"));
        inventory.setItem(48, control(Material.PLAYER_HEAD, "我的投稿"));
        int pending = pendingUploads.forPlayer(player.getUniqueId()).size();
        inventory.setItem(49, control(Material.COMPASS, view.query().isBlank()
                ? "搜索歌曲" : "搜索: " + view.query()));
        inventory.setItem(50, control(Material.PAPER, "待处理投稿: " + pending));
        inventory.setItem(53, control(Material.ARROW, "下一页"));
        player.openInventory(inventory);
        views.put(player.getUniqueId(), view);
    }

    private List<Song> filteredSongs(Player player, View view) {
        List<Song> songs = new ArrayList<>(library.songs());
        if (view.filter() == Filter.RECENT) {
            songs.sort(Comparator.comparingLong(Song::createdAtEpoch).reversed());
        } else if (view.filter() == Filter.MINE) {
            songs.removeIf(song -> !player.getUniqueId().toString().equals(song.minecraftPlayerUuid()));
        } else if (view.filter() == Filter.SEARCH) {
            String query = SongNames.normalize(view.query());
            songs.removeIf(song -> !SongNames.normalize(song.displayLabel() + " "
                    + song.commandName() + " " + String.valueOf(song.minecraftPlayerName())).contains(query));
        }
        return songs;
    }

    private void giveDisc(Player player, Song song) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(discs.createDisc(song));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        resourcePacks.sendSong(player, song);
        player.sendMessage(ChatColor.GREEN + "已获得唱片: " + ChatColor.AQUA + song.displayLabel());
    }

    private static ItemStack control(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        item.setItemMeta(meta);
        return item;
    }

    private enum Filter { ALL, RECENT, MINE, SEARCH }

    private record View(int page, Filter filter, String query) { }
}
