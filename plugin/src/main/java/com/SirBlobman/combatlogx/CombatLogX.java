package com.SirBlobman.combatlogx;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.SirBlobman.combatlogx.api.ICombatLogX;
import com.SirBlobman.combatlogx.api.event.PlayerUntagEvent;
import com.SirBlobman.combatlogx.api.expansion.ExpansionManager;
import com.SirBlobman.combatlogx.api.listener.ICustomDeathListener;
import com.SirBlobman.combatlogx.api.shaded.SirBlobmanAPI;
import com.SirBlobman.combatlogx.api.shaded.configuration.PlayerDataManager;
import com.SirBlobman.combatlogx.api.shaded.nms.MultiVersionHandler;
import com.SirBlobman.combatlogx.api.shaded.utility.MessageUtil;
import com.SirBlobman.combatlogx.api.shaded.utility.Util;
import com.SirBlobman.combatlogx.command.CommandCombatLogX;
import com.SirBlobman.combatlogx.command.CommandCombatTimer;
import com.SirBlobman.combatlogx.command.CustomCommand;
import com.SirBlobman.combatlogx.listener.*;
import com.SirBlobman.combatlogx.utility.CombatManager;
import com.SirBlobman.combatlogx.utility.UpdateChecker;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

public class CombatLogX extends JavaPlugin implements ICombatLogX {
    private static final Map<String, FileConfiguration> fileNameToConfigMap = Util.newMap();
    private final CombatManager combatManager = new CombatManager(this);
    private final ExpansionManager expansionManager = new ExpansionManager(this);
    private final ICustomDeathListener customDeathListener = new ListenerCustomDeath(this);
    
    private final SirBlobmanAPI sirBlobmanAPI = new SirBlobmanAPI(this);
    private final MultiVersionHandler<CombatLogX> multiVersionHandler = new MultiVersionHandler<>(this);
    private final PlayerDataManager<CombatLogX> playerDataManager = new PlayerDataManager<>(this);

    public SirBlobmanAPI getSirBlobmanAPI() {
        return this.sirBlobmanAPI;
    }

    @Override
    public CombatLogX getPlugin() {
        return this;
    }

    @Override
    public void onLoad() {
        saveDefaultConfig("config.yml");
        saveDefaultConfig("language.yml");
        
        ExpansionManager expansionManager = getExpansionManager();
        expansionManager.loadExpansions();
        
        broadcastLoadMessage();
    }

    @Override
    public void onEnable() {
        registerListeners();
        registerCommands();
        registerTasks();
    
        ExpansionManager expansionManager = getExpansionManager();
        expansionManager.enableExpansions();
        
        broadcastEnableMessage();
        UpdateChecker.checkForUpdates(this);
    }

    @Override
    public void onDisable() {
        untagAllPlayers();
        
        ExpansionManager expansionManager = getExpansionManager();
        expansionManager.disableExpansions();
        
        broadcastDisableMessage();
    }

    @Override
    public void registerCommand(String commandName, CommandExecutor executor, String description, String usage, String... aliases) {
        PluginCommand command = getCommand(commandName);
        if(command == null) {
            forceRegisterCommand(commandName, executor, description, usage, aliases);
            return;
        }
        command.setExecutor(executor);

        if(executor instanceof TabCompleter) {
            TabCompleter completer = (TabCompleter) executor;
            command.setTabCompleter(completer);
        }

        if(executor instanceof Listener) {
            Listener listener = (Listener) executor;
            PluginManager manager = Bukkit.getPluginManager();
            manager.registerEvents(listener, this);
        }
    }

    @Override
    public FileConfiguration getConfig(String fileName) {
        FileConfiguration newConfig = fileNameToConfigMap.getOrDefault(fileName, null);
        if(newConfig != null) return newConfig;

        reloadConfig(fileName);
        return getConfig(fileName);
    }

    @Override
    public void reloadConfig(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);

        final InputStream defConfigStream = getResource(fileName);
        if (defConfigStream == null) return;

        newConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)));
        fileNameToConfigMap.put(fileName, newConfig);
    }

    @Override
    public void saveConfig(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        try {
            getConfig(fileName).save(configFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    @Override
    public void saveDefaultConfig(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            saveResource(fileName, false);
        }
    }

    @Override
    public YamlConfiguration getDataFile(OfflinePlayer user) {
        PlayerDataManager<CombatLogX> playerDataManager = getPlayerDataManager();
        return playerDataManager.getData(user);
    }

    @Override
    public void saveDataFile(OfflinePlayer user, YamlConfiguration dataFile) {
        PlayerDataManager<CombatLogX> playerDataManager = getPlayerDataManager();
        playerDataManager.saveData(user);
    }

    @Override
    public String getLanguageMessage(String path) {
        FileConfiguration language = getConfig("language.yml");
        if(language == null) return path;
        if(language.isString(path)) return language.getString(path);
        if(language.isList(path)) {
            List<String> messageList = language.getStringList(path);
            return String.join("\n", messageList);
        }

        return path;
    }

    @Override
    public String getLanguageMessageColored(String path) {
        String message = getLanguageMessage(path);
        if(message == null || message.isEmpty()) return "";

        return MessageUtil.color(message);
    }

    @Override
    public String getLanguageMessageColoredWithPrefix(String path) {
        String message = getLanguageMessage(path);
        if(message == null || message.isEmpty()) return "";

        String prefix = getLanguageMessageColored("prefixes.plugin");
        if(prefix == null || prefix.isEmpty()) return message;

        return MessageUtil.color(prefix + " " + message);
    }

    @Override
    public ClassLoader getPluginClassLoader() {
        return super.getClassLoader();
    }

    @Override
    public CombatManager getCombatManager() {
        return this.combatManager;
    }
    
    @Override
    public ExpansionManager getExpansionManager() {
        return this.expansionManager;
    }

    @Override
    public ICustomDeathListener getCustomDeathListener() {
        return this.customDeathListener;
    }

    @Override
    public void sendMessage(CommandSender sender, String... messages) {
        if(messages == null || messages.length == 0) return;
        for(String message : messages) {
            if(message == null || message.isEmpty()) continue;
            sender.sendMessage(message);
        }
    }
    
    @Override
    public void printDebug(String message) {
        if(message == null || message.isEmpty()) return;
        
        FileConfiguration config = getConfig("config.yml");
        if(!config.getBoolean("debug")) return;
        
        Logger logger = getLogger();
        logger.info("[Debug] " + message);
    }
    
    @Override
    public MultiVersionHandler<CombatLogX> getMultiVersionHandler() {
        return this.multiVersionHandler;
    }
    
    public PlayerDataManager<CombatLogX> getPlayerDataManager() {
        return this.playerDataManager;
    }
    
    private void forceRegisterCommand(String commandName, CommandExecutor executor, String description, String usage, String... aliases) {
        if(commandName == null || executor == null || description == null || usage == null || aliases == null) return;

        PluginManager manager = Bukkit.getPluginManager();

        CustomCommand command = new CustomCommand(commandName, executor, description, usage, aliases);
        manager.registerEvents(command, this);

        if(executor instanceof Listener) {
            Listener listener = (Listener) executor;
            manager.registerEvents(listener, this);
        }
    }
    
    private void registerCommand(String commandName, CommandExecutor executor) {
        registerCommand(commandName, executor, null, null);
    }

    private void broadcastMessage(String message) {
        if(message == null || message.isEmpty()) return;
        String color = MessageUtil.color(message);

        Logger logger = getLogger();
        logger.info(color);

        Collection<? extends Player> playerList = Bukkit.getOnlinePlayers();
        for(Player player : playerList) player.sendMessage(color);
    }

    private void broadcastLoadMessage() {
        FileConfiguration config = getConfig("config.yml");
        boolean shouldBroadcast = config.getBoolean("broadcast.on-load");
        if(!shouldBroadcast) return;

        String message = getLanguageMessageColored("broadcasts.on-load");
        broadcastMessage(message);
    }

    private void broadcastEnableMessage() {
        FileConfiguration config = getConfig("config.yml");
        boolean shouldBroadcast = config.getBoolean("broadcast.on-enable");
        if(!shouldBroadcast) return;

        String message = getLanguageMessageColored("broadcasts.on-enable");
        broadcastMessage(message);
    }

    private void broadcastDisableMessage() {
        FileConfiguration config = getConfig("config.yml");
        boolean shouldBroadcast = config.getBoolean("broadcast.on-disable");
        if(!shouldBroadcast) return;

        String message = getLanguageMessageColored("broadcasts.on-disable");
        broadcastMessage(message);
    }

    private void untagAllPlayers() {
        CombatManager manager = getCombatManager();
        List<Player> playerList = Bukkit.getOnlinePlayers().stream().filter(manager::isInCombat).collect(Collectors.toList());
        playerList.forEach(player -> manager.untag(player, PlayerUntagEvent.UntagReason.EXPIRE));
    }

    private void registerListeners() {
        PluginManager manager = Bukkit.getPluginManager();
        manager.registerEvents(new ListenerAttack(this), this);
        manager.registerEvents(new ListenerCombatChecks(this), this);
        manager.registerEvents(new ListenerPunishChecks(this), this);
        manager.registerEvents(new ListenerUntagger(this), this);
        manager.registerEvents(customDeathListener, this);
    }

    private void registerCommands() {
        registerCommand("combatlogx", new CommandCombatLogX(this));
        registerCommand("combattimer", new CommandCombatTimer(this));
    }

    private void registerTasks() {
        BukkitScheduler scheduler = Bukkit.getScheduler();

        CombatManager combatManager = getCombatManager();
        scheduler.runTaskTimer(this, combatManager, 0L, 10L);
    }
}