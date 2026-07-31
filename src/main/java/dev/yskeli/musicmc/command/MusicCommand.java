package dev.yskeli.musicmc.command;

import dev.yskeli.musicmc.admin.AdminStateStore;
import dev.yskeli.musicmc.catalog.CatalogSyncService;
import dev.yskeli.musicmc.config.PluginSettings;
import dev.yskeli.musicmc.gui.SongBrowserMenu;
import dev.yskeli.musicmc.library.SongLibrary;
import dev.yskeli.musicmc.model.Song;
import dev.yskeli.musicmc.model.SongNames;
import dev.yskeli.musicmc.resource.ResourcePackService;
import dev.yskeli.musicmc.ticket.PendingUpload;
import dev.yskeli.musicmc.ticket.PendingUploadStore;
import dev.yskeli.musicmc.ticket.UploadTicket;
import dev.yskeli.musicmc.ticket.UploadTicketSigner;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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
    private final AdminStateStore adminState;
    private final SongBrowserMenu browser;

    public MusicCommand(
            PluginSettings settings,
            SongLibrary library,
            PendingUploadStore pendingUploads,
            Optional<UploadTicketSigner> signer,
            CatalogSyncService catalogSync,
            ResourcePackService resourcePacks,
            AdminStateStore adminState,
            SongBrowserMenu browser) {
        this.settings = settings;
        this.library = library;
        this.pendingUploads = pendingUploads;
        this.signer = signer;
        this.catalogSync = catalogSync;
        this.resourcePacks = resourcePacks;
        this.adminState = adminState;
        this.browser = browser;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                browser.open(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "upload" -> upload(sender, args);
            case "netease" -> netease(sender, args);
            case "browse" -> browse(sender);
            case "search" -> search(sender, args);
            case "list" -> list(sender, args);
            case "status" -> status(sender);
            case "cancel" -> cancel(sender);
            case "sync" -> sync(sender);
            case "pack" -> pack(sender);
            case "admin" -> admin(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean upload(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /music upload <歌曲命令名称>");
            return true;
        }
        return createSubmission(sender, join(args, 1), "upload", "", null);
    }

    private boolean netease(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].matches("[1-9][0-9]{0,19}")) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /music netease <网易云歌曲ID> <歌曲命令名称>");
            return true;
        }
        return createSubmission(sender, join(args, 2), "netease", args[1], null);
    }

    private boolean createSubmission(
            CommandSender sender, String requestedName, String source, String providerId, Song replacement) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "这个命令只能由玩家执行。");
            return true;
        }
        if (!player.hasPermission("musicmc.upload")) {
            player.sendMessage(ChatColor.RED + "你没有投稿歌曲的权限。");
            return true;
        }
        if (adminState.isUploadBanned(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "管理员已禁止你投稿歌曲。");
            return true;
        }
        if (signer.isEmpty()) {
            player.sendMessage(ChatColor.RED + "服务器尚未配置上传签名密钥，请联系管理员。");
            return true;
        }
        final String songName;
        try {
            songName = SongNames.validateDisplayName(requestedName);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            return true;
        }
        pendingUploads.pruneExpired();
        if (replacement == null && library.findByName(songName).isPresent()) {
            player.sendMessage(ChatColor.RED + "这个歌曲命令名称已经存在。");
            return true;
        }
        if (pendingUploads.hasPendingName(songName)) {
            player.sendMessage(ChatColor.RED + "这个歌曲命令名称已有待处理投稿。");
            return true;
        }
        if (pendingUploads.hasPendingPlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "你已有一个待处理投稿；完成后可以继续投稿，总次数不限。");
            return true;
        }
        try {
            UploadTicket ticket = replacement == null
                    ? signer.orElseThrow().create(player.getUniqueId(), player.getName(), songName)
                    : signer.orElseThrow().createReplacement(player.getUniqueId(), player.getName(), songName,
                            replacement.songId(), replacement.revision() + 1);
            pendingUploads.add(new PendingUpload(ticket.nonce(), player.getUniqueId(), songName, ticket.expiresAt()));
            sendSubmissionLink(player, songName, source, providerId, ticket);
        } catch (GeneralSecurityException exception) {
            player.sendMessage(ChatColor.RED + "生成上传凭证失败，请联系管理员查看日志。");
        }
        return true;
    }

    private void sendSubmissionLink(
            Player player, String songName, String source, String providerId, UploadTicket ticket) {
        player.sendMessage(ChatColor.GOLD + "已为歌曲“" + songName + "”生成投稿凭证。");
        player.sendMessage(ChatColor.GRAY + "凭证有效至 " + ticket.expiresAt() + "，且只能使用一次。");
        String separator = settings.issueUrl().contains("?") ? "&" : "?";
        String url = settings.issueUrl() + separator
                + "ticket=" + encode(ticket.encodedTicket())
                + "&song=" + encode(songName)
                + "&player=" + encode(player.getName())
                + "&source=" + encode(source)
                + (providerId.isBlank() ? "" : "&providerId=" + encode(providerId));
        TextComponent open = new TextComponent(ChatColor.GREEN + "[打开 MusicMC 自动投稿页面]");
        open.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        player.spigot().sendMessage(open);
        TextComponent copy = new TextComponent(ChatColor.AQUA + "[复制投稿凭证]");
        copy.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, ticket.encodedTicket()));
        player.spigot().sendMessage(copy);
    }

    private boolean browse(CommandSender sender) {
        if (sender instanceof Player player) {
            browser.open(player);
        } else {
            sender.sendMessage(ChatColor.RED + "这个命令只能由玩家执行。");
        }
        return true;
    }

    private boolean search(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "这个命令只能由玩家执行。");
        } else if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "用法: /music search <关键词>");
        } else {
            browser.openSearch(player, join(args, 1));
        }
        return true;
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
            sender.sendMessage(ChatColor.AQUA + "- " + song.displayLabel()
                    + ChatColor.DARK_GRAY + " [" + song.commandName() + "]"
                    + ChatColor.GRAY + " / 上传者 " + song.minecraftPlayerName());
        }
        return true;
    }

    private boolean status(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "MusicMC 状态");
        sender.sendMessage(ChatColor.GRAY + "可用歌曲: " + ChatColor.WHITE + library.size());
        sender.sendMessage(ChatColor.GRAY + "目录同步: " + ChatColor.WHITE
                + (catalogSync.isSyncing() ? "进行中" : "空闲"));
        sender.sendMessage(ChatColor.GRAY + "投稿签名: " + ChatColor.WHITE
                + (signer.isPresent() ? "可用" : "未配置"));
        if (sender instanceof Player player) {
            List<PendingUpload> pending = pendingUploads.forPlayer(player.getUniqueId());
            sender.sendMessage(ChatColor.GRAY + "我的待提交凭证: " + ChatColor.WHITE + pending.size());
            for (PendingUpload upload : pending) {
                long seconds = Math.max(0, upload.expiresAt().getEpochSecond() - Instant.now().getEpochSecond());
                sender.sendMessage(ChatColor.DARK_GRAY + "- " + upload.songName() + "（剩余 " + seconds + " 秒）");
            }
        }
        return true;
    }

    private boolean cancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "这个命令只能由玩家执行。");
            return true;
        }
        int removed = pendingUploads.removeForPlayer(player.getUniqueId());
        player.sendMessage(removed > 0
                ? ChatColor.GREEN + "已清除本地待提交凭证；正在运行的 GitHub 任务不会被取消。"
                : ChatColor.GRAY + "没有可清除的本地待提交凭证。");
        return true;
    }

    private boolean sync(CommandSender sender) {
        if (!sender.hasPermission("musicmc.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有手动同步目录的权限。");
        } else {
            catalogSync.syncNow(sender);
        }
        return true;
    }

    private boolean pack(CommandSender sender) {
        if (sender instanceof Player player) {
            resourcePacks.resendAll(player);
            player.sendMessage(ChatColor.GRAY + "已重新发送所有 MusicMC 歌曲资源包。");
        } else {
            sender.sendMessage(ChatColor.RED + "这个命令只能由玩家执行。");
        }
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("musicmc.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有 MusicMC 管理权限。");
            return true;
        }
        if (args.length < 2) {
            sendAdminHelp(sender);
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "disable", "remove" -> setSongState(sender, args, true);
            case "enable" -> setSongState(sender, args, false);
            case "replace" -> replaceSong(sender, args);
            case "banupload" -> setUploadBan(sender, args, true);
            case "unbanupload" -> setUploadBan(sender, args, false);
            case "pending" -> adminPending(sender);
            case "uploader" -> adminUploader(sender, args);
            case "storage" -> adminStorage(sender);
            default -> {
                sendAdminHelp(sender);
                yield true;
            }
        };
    }

    private boolean setSongState(CommandSender sender, String[] args, boolean disabled) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /music admin "
                    + (disabled ? "disable" : "enable") + " <歌曲命令名称>");
            return true;
        }
        String name = join(args, 2);
        Song song = library.allSongs().stream()
                .filter(item -> SongNames.normalize(item.commandName()).equals(SongNames.normalize(name)))
                .findFirst().orElse(null);
        if (song == null) {
            sender.sendMessage(ChatColor.RED + "没有找到歌曲: " + name);
            return true;
        }
        adminState.setSongDisabled(song.songId(), disabled);
        library.setLocallyDisabled(song.songId(), disabled);
        sender.sendMessage(ChatColor.GREEN + (disabled ? "已在本服务器禁用: " : "已在本服务器启用: ")
                + song.displayLabel());
        return true;
    }

    private boolean replaceSong(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "替换歌曲必须由游戏内管理员执行。");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /music admin replace upload <歌曲命令名称>");
            sender.sendMessage(ChatColor.YELLOW + "或: /music admin replace netease <网易云ID> <歌曲命令名称>");
            return true;
        }
        String source = args[2].toLowerCase(Locale.ROOT);
        String providerId = "";
        int nameStart = 3;
        if (source.equals("netease")) {
            if (args.length < 5 || !args[3].matches("[1-9][0-9]{0,19}")) {
                sender.sendMessage(ChatColor.RED + "网易云歌曲 ID 无效。");
                return true;
            }
            providerId = args[3];
            nameStart = 4;
        } else if (!source.equals("upload")) {
            sender.sendMessage(ChatColor.RED + "替换来源只能是 upload 或 netease。");
            return true;
        }
        String commandName = join(args, nameStart);
        Song replacement = library.allSongs().stream()
                .filter(song -> SongNames.normalize(song.commandName()).equals(SongNames.normalize(commandName)))
                .findFirst().orElse(null);
        if (replacement == null) {
            sender.sendMessage(ChatColor.RED + "没有找到歌曲: " + commandName);
            return true;
        }
        return createSubmission(player, replacement.commandName(), source, providerId, replacement);
    }

    @SuppressWarnings("deprecation")
    private boolean setUploadBan(CommandSender sender, String[] args, boolean banned) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /music admin "
                    + (banned ? "banupload" : "unbanupload") + " <玩家>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        adminState.setUploadBanned(target.getUniqueId(), banned);
        sender.sendMessage(ChatColor.GREEN + (banned ? "已禁止投稿: " : "已解除投稿封禁: ") + args[2]);
        return true;
    }

    private boolean adminPending(CommandSender sender) {
        pendingUploads.pruneExpired();
        sender.sendMessage(ChatColor.GOLD + "本地待提交凭证（GitHub 队列请在仓库 Issues 查看）");
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            int count = pendingUploads.forPlayer(player.getUniqueId()).size();
            if (count > 0) sender.sendMessage(ChatColor.GRAY + "- " + player.getName() + ": " + count);
        }
        return true;
    }

    private boolean adminUploader(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /music admin uploader <玩家>");
            return true;
        }
        List<Song> songs = library.allSongs().stream()
                .filter(song -> args[2].equalsIgnoreCase(String.valueOf(song.minecraftPlayerName())))
                .toList();
        sender.sendMessage(ChatColor.GOLD + args[2] + " 上传的歌曲: " + songs.size());
        songs.forEach(song -> sender.sendMessage(ChatColor.GRAY + "- " + song.displayLabel()
                + " [" + song.commandName() + "]"));
        return true;
    }

    private boolean adminStorage(CommandSender sender) {
        long bytes = 0;
        try (var paths = Files.walk(adminState.dataDirectory().toPath())) {
            bytes = paths.filter(Files::isRegularFile).mapToLong(path -> {
                try { return Files.size(path); } catch (IOException ignored) { return 0; }
            }).sum();
        } catch (IOException ignored) {
            sender.sendMessage(ChatColor.RED + "无法读取插件目录占用。");
        }
        long free = adminState.dataDirectory().getUsableSpace();
        sender.sendMessage(ChatColor.GOLD + "MusicMC 存储");
        sender.sendMessage(ChatColor.GRAY + "本地元数据: " + formatBytes(bytes));
        sender.sendMessage(ChatColor.GRAY + "磁盘可用空间: " + formatBytes(free));
        sender.sendMessage(ChatColor.GRAY + "歌曲音频与资源包: 外部存储按需直连，不在服务器本地永久保存");
        return true;
    }

    private static void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "MusicMC 命令");
        sender.sendMessage(ChatColor.YELLOW + "/music" + ChatColor.GRAY + " - 打开歌曲菜单");
        sender.sendMessage(ChatColor.YELLOW + "/music upload <名称>" + ChatColor.GRAY + " - 投稿 ZIP 音乐");
        sender.sendMessage(ChatColor.YELLOW + "/music netease <ID> <名称>" + ChatColor.GRAY + " - 投稿网易云歌曲");
        sender.sendMessage(ChatColor.YELLOW + "/music search <关键词>" + ChatColor.GRAY + " - 搜索歌曲");
        sender.sendMessage(ChatColor.YELLOW + "/music list [页码]" + ChatColor.GRAY + " - 查看歌曲列表");
        sender.sendMessage(ChatColor.YELLOW + "/music status" + ChatColor.GRAY + " - 查看同步与投稿状态");
        sender.sendMessage(ChatColor.YELLOW + "/music cancel" + ChatColor.GRAY + " - 清除本地待提交凭证");
        sender.sendMessage(ChatColor.YELLOW + "/music pack" + ChatColor.GRAY + " - 重新加载全部歌曲资源包");
        sender.sendMessage(ChatColor.YELLOW + "/getcd <名称>" + ChatColor.GRAY + " - 获取唱片");
    }

    private static void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "MusicMC 管理命令");
        sender.sendMessage(ChatColor.YELLOW + "/music admin disable|enable|remove <歌曲>");
        sender.sendMessage(ChatColor.YELLOW + "/music admin replace upload|netease [...] <歌曲>");
        sender.sendMessage(ChatColor.YELLOW + "/music admin banupload|unbanupload <玩家>");
        sender.sendMessage(ChatColor.YELLOW + "/music admin pending|uploader <玩家>|storage");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> choices = new ArrayList<>(List.of("browse", "search", "list", "status", "cancel", "pack"));
            if (sender.hasPermission("musicmc.upload")) choices.addAll(List.of("upload", "netease"));
            if (sender.hasPermission("musicmc.admin")) choices.addAll(List.of("sync", "admin"));
            return filterPrefix(choices, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("musicmc.admin")) {
            return filterPrefix(List.of("disable", "enable", "remove", "replace", "banupload", "unbanupload",
                    "pending", "uploader", "storage"), args[1]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("admin")
                && List.of("disable", "enable", "remove").contains(args[1].toLowerCase(Locale.ROOT))) {
            return completeSongName(args, 2);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return filterPrefix(List.of("1", "2", "3"), args[1]);
        }
        return List.of();
    }

    private List<String> completeSongName(String[] args, int start) {
        String entered = join(args, start);
        int lastSpace = entered.lastIndexOf(' ');
        return library.allSongs().stream().map(Song::commandName)
                .filter(name -> SongNames.normalize(name).startsWith(SongNames.normalize(entered)))
                .sorted(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)))
                .map(name -> lastSpace < 0 ? name : name.substring(Math.min(lastSpace + 1, name.length())))
                .distinct().toList();
    }

    private static List<String> filterPrefix(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private static String join(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String formatBytes(long value) {
        if (value < 1024) return value + " B";
        if (value < 1024L * 1024) return "%.1f KiB".formatted(value / 1024.0);
        if (value < 1024L * 1024 * 1024) return "%.1f MiB".formatted(value / 1024.0 / 1024.0);
        return "%.1f GiB".formatted(value / 1024.0 / 1024.0 / 1024.0);
    }
}
