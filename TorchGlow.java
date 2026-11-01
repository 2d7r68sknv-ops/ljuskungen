package se.torchglow;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Experimental supplemental torch lighting for Paper. */
public final class TorchGlow extends JavaPlugin implements Listener {
    private final Map<String, Set<String>> byTorch = new HashMap<>();
    private final Map<String, Integer> lampRefs = new HashMap<>();
    private File database;
    private int radius, spacing, maxLights;

    @Override public void onEnable() {
        saveDefaultConfig();
        radius = Math.max(4, Math.min(30, getConfig().getInt("radius", 30)));
        spacing = Math.max(6, Math.min(14, getConfig().getInt("spacing", 12)));
        maxLights = Math.max(1, Math.min(120, getConfig().getInt("max-lights-per-torch", 75)));
        database = new File(getDataFolder(), "lights.txt");
        loadRecords();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TorchGlow enabled. Only newly placed torches are detected; use /torchglow scan for nearby existing torches.");
    }
    @Override public void onDisable() { saveRecords(); }

    private boolean isTorch(Material type) {
        return type == Material.TORCH || type == Material.WALL_TORCH ||
               type == Material.SOUL_TORCH || type == Material.SOUL_WALL_TORCH;
    }
    private String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
    private Block block(String key) {
        try {
            String[] t = key.split(":");
            World w = Bukkit.getWorld(UUID.fromString(t[0]));
            if (w == null) return null;
            int x = Integer.parseInt(t[1]), y = Integer.parseInt(t[2]), z = Integer.parseInt(t[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
            return w.getBlockAt(x, y, z);
        } catch (Exception e) { return null; }
    }
    private String at(World world, int x, int y, int z) {
        return world.getUID() + ":" + x + ":" + y + ":" + z;
    }

    @EventHandler(ignoreCancelled = true)
    public void placed(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (lampRefs.containsKey(key(b))) {
            // A player may replace a virtual lamp with a real block.
            detachLamp(key(b));
        }
        if (isTorch(b.getType())) {
            Bukkit.getScheduler().runTask(this, () -> addTorch(b));
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (isTorch(b.getType())) removeTorch(key(b));
        else if (lampRefs.containsKey(key(b))) detachLamp(key(b));
    }

    private void addTorch(Block torch) {
        if (!isTorch(torch.getType()) || byTorch.containsKey(key(torch))) return;
        World w = torch.getWorld();
        Set<String> lamps = new LinkedHashSet<>();
        // Distribute supplemental lights in a sphere around the torch, no forced chunk loads.
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += spacing)
            for (int dy = -radius; dy <= radius; dy += spacing)
                for (int dz = -radius; dz <= radius; dz += spacing) {
                    double d2 = dx*dx + dy*dy + dz*dz;
                    if (d2 > 0 && d2 <= radius*radius) offsets.add(new int[]{dx,dy,dz});
                }
        offsets.sort(Comparator.comparingInt(a -> a[0]*a[0] + a[1]*a[1] + a[2]*a[2]));
        for (int[] a : offsets) {
            if (lamps.size() >= maxLights) break;
            int x = torch.getX()+a[0], y = torch.getY()+a[1], z = torch.getZ()+a[2];
            if (y < w.getMinHeight() || y >= w.getMaxHeight() || !w.isChunkLoaded(x>>4,z>>4)) continue;
            Block b = w.getBlockAt(x,y,z);
            String location = key(b);
            if (b.getType() != Material.AIR && !(b.getType() == Material.LIGHT && lampRefs.containsKey(location))) continue;
            if (!visible(torch, x, y, z)) continue;
            if (!lampRefs.containsKey(location)) {
                if (b.getType() != Material.AIR) continue;
                b.setBlockData(makeLight(), false);
            }
            lampRefs.merge(location, 1, Integer::sum);
            lamps.add(location);
        }
        byTorch.put(key(torch), lamps);
        saveRecords();
    }
    private org.bukkit.block.data.BlockData makeLight() {
        Levelled data = (Levelled) Bukkit.createBlockData(Material.LIGHT);
        data.setLevel(15);
        return data;
    }
    private boolean visible(Block origin, int x, int y, int z) {
        // Avoid placing light behind opaque walls. Transparent glass/water are allowed.
        World world = origin.getWorld();
        double sx = origin.getX()+.5, sy=origin.getY()+.5, sz=origin.getZ()+.5;
        double dx=x+.5-sx, dy=y+.5-sy, dz=z+.5-sz;
        int steps=(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)*2);
        for(int i=2;i<steps;i++) {
            double f=(double)i/steps;
            int px=(int)Math.floor(sx+dx*f), py=(int)Math.floor(sy+dy*f), pz=(int)Math.floor(sz+dz*f);
            if(!world.isChunkLoaded(px>>4,pz>>4) || world.getBlockAt(px,py,pz).getType().isOccluding()) return false;
        }
        return true;
    }
    private void removeTorch(String origin) {
        Set<String> lamps = byTorch.remove(origin);
        if(lamps == null) return;
        for(String position : lamps) {
            int count=lampRefs.getOrDefault(position,0);
            if (count > 1) lampRefs.put(position,count-1);
            else {
                lampRefs.remove(position);
                Block b=block(position);
                if (b != null && b.getType() == Material.LIGHT) b.setType(Material.AIR, false);
                // Unloaded chunks will retain lights until next cleanup/scan.
            }
        }
        saveRecords();
    }
    private void detachLamp(String position) {
        lampRefs.remove(position);
        for (Set<String> lamps : byTorch.values()) lamps.remove(position);
        saveRecords();
    }
    private void loadRecords() {
        if(!database.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(database), StandardCharsets.UTF_8))) {
            String line;
            while((line=r.readLine())!=null) {
                String[] items=line.split("\\|", 2);
                if(items.length!=2) continue;
                Set<String> lamps=new LinkedHashSet<>();
                if(!items[1].isEmpty()) Collections.addAll(lamps,items[1].split(","));
                byTorch.put(items[0],lamps);
                for(String pos:lamps) lampRefs.merge(pos,1,Integer::sum);
            }
        } catch(IOException ex) {getLogger().warning("Could not load lights.txt: "+ex.getMessage());}
    }
    private void saveRecords() {
        getDataFolder().mkdirs();
        File tmp = new File(getDataFolder(),"lights.tmp");
        try(PrintWriter w=new PrintWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            for(var e:byTorch.entrySet()) w.println(e.getKey()+"|"+String.join(",",e.getValue()));
        }catch(IOException ex){getLogger().warning("Could not save: "+ex.getMessage());return;}
        if(!tmp.renameTo(database)) {
            try {java.nio.file.Files.move(tmp.toPath(),database.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);} catch(IOException ex){getLogger().warning(ex.getMessage());}
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("torchglow.admin")) {sender.sendMessage("No permission");return true;}
        if(args.length == 0) {sender.sendMessage("/torchglow scan | clear | status");return true;}
        if(args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("TorchGlow: "+byTorch.size()+" tracked torches, "+lampRefs.size()+" virtual lights.");return true;
        }
        if(args[0].equalsIgnoreCase("clear")) {
            // Clear only plugin-owned LIGHT blocks in currently loaded chunks.
            for(String location:new ArrayList<>(lampRefs.keySet())) {
                Block b=block(location);
                if (b != null && b.getType()==Material.LIGHT) b.setType(Material.AIR,false);
            }
            byTorch.clear();lampRefs.clear();saveRecords();
            sender.sendMessage("Removed tracked virtual lights from loaded chunks. Unloaded chunks may still contain lights.");return true;
        }
        if(args[0].equalsIgnoreCase("scan") && sender instanceof org.bukkit.entity.Player player) {
            int found=0;
            World w=player.getWorld();
            int cx=player.getLocation().getBlockX(), cy=player.getLocation().getBlockY(),cz=player.getLocation().getBlockZ();
            // Limit scans to a small vicinity; users can repeat after moving.
            for(int x=cx-12;x<=cx+12;x++) for(int y=Math.max(w.getMinHeight(),cy-12);y<=Math.min(w.getMaxHeight()-1,cy+12);y++) for(int z=cz-12;z<=cz+12;z++) {
                if(!w.isChunkLoaded(x>>4,z>>4))continue;
                Block b=w.getBlockAt(x,y,z);
                if(isTorch(b.getType())&&!byTorch.containsKey(key(b))) {addTorch(b);found++;}
            }
            sender.sendMessage("Registered "+found+" existing torches nearby.");return true;
        }
        sender.sendMessage("Use /torchglow scan, /torchglow status or /torchglow clear");return true;
    }
}
