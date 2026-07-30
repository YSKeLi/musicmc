package dev.yskeli.musicmc.command;

import dev.yskeli.musicmc.catalog.CatalogSyncService;
import dev.yskeli.musicmc.config.PluginSettings;
import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongNames;
import dev.yskeli.musicmc.resource.ResourcePackService;
import dev.yskeli.musicmc.ticket.PendingUpload;
import dev.yskeli.musicmc.ticket.PendingUploadStore;
import dev.yskeli.musicmc.ticket.UploadTicket;
import dev.yskeli.musicmc.ticket.UploadTicketSigner;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MusicCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 8;

    private final PluginSettings settings;
    private final SongLibrary library;
    private final PendingUploadStore pendingUploads;
    private final Optional<UploadTicketSigner> signer;
    private final CatalogSyncService catalogSync;
    private final ResourcePackService resourcePacks;

    public MusicCommand(
            PluginSettings settings,
            SongLibrary library,
            PendingUploadStore pendingUploads,
            Optional<UploadTicketSigner> signer,
            CatalogSyncService catalogSync,
            ResourcePackService resourcePacks) {
        this.settings = settings;
        this.library = library;
        this.pendingUploads = pendingUploads;
        this.signer = signer;
        this.catalogSync = catalogSync;
        this.resourcePacks = resourcePacks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "upload" -> upload(sender, args);
            case "list" -> list(sender, args);
            case "status" -> status(sender);
            case "sync" -> sync(sender);
            case "pack" -> pack(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean upload(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "这个命令只能由玩家执行。");
            return true;
        }
        if (!player.hasPermission("musicmc.upload")) {
            player.sendMessage(ChatColor.RED + "你没有上传歌曲的权限。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "用法: /music upload <歌曲名称>");
            return true;
        }
        if (signer.isEmpty()) {
            player.sendMessage(ChatColor.RED + "服务器尚未配置上传签名密钥，请联系管理员。");
            return true;
        }

        final String songName;
        try {
            songName = SongNames.validateDisplayName(join(args, 1));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            return true;
        }
        pendingUploads.pruneExpired();
        if (library.findByName(songName).isPresent()) {
            player.sendMessage(ChatColor.RED + "歌曲名称已经存在，请换一个名称。");
            return true;
        }
        if (pendingUploads.hasPendingName(songName)) {
            player.sendMessage(ChatColor.RED + "这个歌曲名称已有待处理的上传。");
            return true;
        }
        try {
            UploadTicket ticket = signer.orElseThrow().create(player.getUniqueId(), player.getName(), songName);
            pendingUploads.add(new PendingUpload(ticket.nonce(), player.getUniqueId(), songName, ticket.expiresAt()));
            sendUploadLinks(player, songName, ticket);
        } catch (GeneralSecurityException exception) {
            player.sendMessage(ChatColor.RED + "生成上传凭证失败，请联系管理员查看日志。");
        }
        return true;
    }

    private void sendUploadLinks(Player player, String songName, UploadTicket ticket) {
        player.sendMessage(ChatColor.GOLD + "已为歌曲 “" + songName + "” 生成上传凭证。");
        player.sendMessage(ChatColor.GRAY + "凭证有效至 " + ticket.expiresAt() + "，只能用于这名玩家和这首歌。");

        TextComponent copy = new TextComponent(ChatColor.AQUA + "[点击复制上传凭证]");
        copy.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, ticket.encodedTicket()));
        player.spigot().sendMessage(copy);

        String separator = settings.issueUrl().contains("?") ? "&" : "?";
        String url = settings.issueUrl() + separator
                + "ticket=" + encode(ticket.encodedTicket())
                + "&song=" + encode(songName)
                + "&player=" + encode(player.getName());
        TextComponent open = new TextComponent(ChatColor.GREEN + "[打开 MusicMC 上传页]");
        open.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        player.spigot().sendMessage(open);
        player.sendMessage(ChatColor.GRAY + "页面会自动填写歌曲信息；选择名称相同的 ZIP 后即可自动提交。");
    }

    private boolean list(CommandSender sender, String[] args) {
        int requestedPage = 1;
        if (args.length >= 2) {
            try {
                requestedPage = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(ChatColor.RED + "页码必须是整数。");
                return true;
            }
        }
        List<Song> songs = library.songs();
        int pages = Math.max(1, (songs.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(pages, requestedPage));
        sender.sendMessage(ChatColor.GOLD + "MusicMC 歌曲列表 " + page + "/" + pages
                + ChatColor.GRAY + "（共 " + songs.size() + " 首）");
        int start = (page - 1) * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, songs.size()); index++) {
            Song song = songs.get(index);
            sender.sendMessage(ChatColor.AQUA + "- " + song.displayName()
                    + ChatColor.GRAY + " / 上传者 " + song.minecraftPlayerName());
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "MusicMC 状态");
        sender.sendMessage(ChatColor.GRAY + "可用歌曲: " + ChatColor.WHITE + library.size());
        sender.sendMessage(ChatColor.GRAY + "目录同步: " + ChatColor.WHITE
                + (catalogSync.isSyncing() ? "进行中" : "空闲"));
        sender.sendMessage(ChatColor.GRAY + "上传签名: " + ChatColor.WHITE
                + (signer.isPresent() ? "可用" : "未配置"));
        if (sender instanceof Player player) {
            List<PendingUpload> pending = pendingUploads.forPlayer(player.getUniqueId());
            sender.sendMessage(ChatColor.GRAY + "我的待处理上传: " + ChatColor.WHITE + pending.size());
            for (PendingUpload upload : pending) {
                long seconds = Math.max(0, upload.expiresAt().getEpochSecond() - Instant.now().getEpochSecond());
                sender.sendMessage(ChatColor.DARK_GRAY + "- " + upload.songName() + "（凭证剩余 " + seconds + " 秒）");
            }
        }
        return true;
    }

    private boolean sync(CommandSender sender) {
        if (!sender.hasPermission("musicmc.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有手动同步目录的权限。");
            return true;
        }
        catalogSync.syncNow(sender);
        return true;
    }

    private boolean pack(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "这个命令只能由玩家执行。");
            return true;
        }
        resourcePacks.resendAll(player);
        player.sendMessage(ChatColor.GRAY + "已重新发送 MusicMC 歌曲资源包。");
        return true;
    }

    private static void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "MusicMC 命令");
        sender.sendMessage(ChatColor.YELLOW + "/music upload <歌曲名称>" + ChatColor.GRAY + " - 创建 GitHub 上传凭证");
        sender.sendMessage(ChatColor.YELLOW + "/music list [页码]" + ChatColor.GRAY + " - 查看歌曲列表");
        sender.sendMessage(ChatColor.YELLOW + "/music status" + ChatColor.GRAY + " - 查看同步和上传状态");
        sender.sendMessage(ChatColor.YELLOW + "/music pack" + ChatColor.GRAY + " - 重新加载歌曲资源包");
        sender.sendMessage(ChatColor.YELLOW + "/getcd <歌曲名称>" + ChatColor.GRAY + " - 获取唱片");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> choices = new ArrayList<>(List.of("list", "status", "pack"));
            if (sender.hasPermission("musicmc.upload")) {
                choices.add("upload");
            }
            if (sender.hasPermission("musicmc.admin")) {
                choices.add("sync");
            }
            return filterPrefix(choices, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return filterPrefix(List.of("1", "2", "3"), args[1]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private static String join(String[] args, int start) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
